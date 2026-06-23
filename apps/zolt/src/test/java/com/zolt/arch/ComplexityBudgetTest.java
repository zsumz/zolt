package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ComplexityBudgetTest {
    private static final Path BUDGETS =
            RepositoryPaths.appRoot().resolve("src/test/resources/com/zolt/arch/complexity-budgets.txt");
    private static final Path ALLOWLIST =
            RepositoryPaths.appRoot().resolve("src/test/resources/com/zolt/arch/complexity-allowlist.txt");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+)(?:\\.\\*)?\\s*;");
    private static final Pattern PUBLIC_METHOD_PATTERN = Pattern.compile(
            "^\\s*public\\s+(?:static\\s+|final\\s+|synchronized\\s+|abstract\\s+)*[\\w<>, ?\\[\\].]+\\s+(\\w+)\\s*\\(");
    private static final Pattern NESTED_TYPE_PATTERN = Pattern.compile(
            "^\\s+(?:public\\s+|private\\s+|protected\\s+|static\\s+|final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+)*(?:class|record|enum|interface)\\s+\\w+.*");

    @Test
    void productionSourcesStayWithinComplexityBudgets() throws IOException {
        List<Budget> budgets = readBudgets(BUDGETS);
        Map<String, AllowlistEntry> allowlist = readAllowlist(ALLOWLIST);
        Map<String, SourceComplexity> overBudgetSources = overBudgetSources(budgets);
        List<String> violations = new ArrayList<>();

        for (SourceComplexity complexity : overBudgetSources.values()) {
            AllowlistEntry entry = allowlist.get(complexity.path());
            if (entry == null) {
                violations.add(complexity.path()
                        + " exceeds complexity budget: "
                        + complexity.describeMetrics()
                        + ". Extract behavior, introduce a value object, or add a planned exception.");
            } else if (complexity.imports() > entry.maxImports()
                    || complexity.publicMethods() > entry.maxPublicMethods()
                    || complexity.constructorParameters() > entry.maxConstructorParameters()
                    || complexity.nestedTypes() > entry.maxNestedTypes()) {
                violations.add(complexity.path()
                        + " grew beyond allowlisted complexity ["
                        + entry.followUp()
                        + "]: "
                        + complexity.describeMetrics());
            }
        }

        allowlist.keySet().stream()
                .filter(path -> !overBudgetSources.containsKey(path))
                .sorted()
                .forEach(path -> violations.add(path + " is no longer over the complexity budget; remove the allowlist entry"));

        assertTrue(
                violations.isEmpty(),
                () -> "Complexity budget violations:\n"
                        + describe(violations)
                        + "\nPrefer narrower collaborators, value objects, or a planned policy exception.");
    }

    @Test
    void scannerCountsImportsPublicMethodsConstructorParametersAndNestedTypes(@TempDir Path tempDir)
            throws IOException {
        Path source = tempDir.resolve("src/main/java/com/example/FanOutOwner.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.example;

                import java.nio.file.Path;
                import java.util.List;
                import java.util.Map;

                public final class FanOutOwner {
                    public static final String NAME = "fan-out";

                    public FanOutOwner(
                            Path root,
                            List<String> values,
                            Map<String, String> labels) {
                    }

                    public String render() {
                        return NAME;
                    }

                    public static int count() {
                        return 1;
                    }

                    private static final class Nested {
                    }
                }
                """);

        assertEquals(
                new SourceComplexity(
                        RepositoryPaths.displayPath(source),
                        3,
                        2,
                        3,
                        1),
                scan(source));
    }

    @Test
    void scannerAvoidsFalsePositiveFieldsAndConstructors(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("src/main/java/com/example/Value.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.example;

                public final class Value {
                    public static final String NAME = "value";

                    public Value(String value) {
                    }

                    public String value() {
                        return NAME;
                    }
                }
                """);

        assertEquals(
                new SourceComplexity(RepositoryPaths.displayPath(source), 0, 1, 1, 0),
                scan(source));
    }

    @Test
    void budgetFileParserReadsRootPatternsAndThresholds(@TempDir Path tempDir) throws IOException {
        Path budgets = tempDir.resolve("budgets.txt");
        Files.writeString(budgets, """
                # rootPattern|maxImports|maxPublicMethods|maxConstructorParameters|maxNestedTypes
                apps/*/src/main/java|45|30|10|20

                modules/*/src/main/java|40|25|8|12
                """);

        assertEquals(
                List.of(
                        new Budget(Path.of("apps/*/src/main/java"), 45, 30, 10, 20),
                        new Budget(Path.of("modules/*/src/main/java"), 40, 25, 8, 12)),
                readBudgets(budgets));
    }

    @Test
    void allowlistParserReadsTrackedExceptions(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, """
                # path|maxImports|maxPublicMethods|maxConstructorParameters|maxNestedTypes|followUp|reason
                modules/zolt-build/src/main/java/com/zolt/build/Large.java|50|35|12|22||legacy coordinator
                """);

        assertEquals(
                Map.of(
                        "modules/zolt-build/src/main/java/com/zolt/build/Large.java",
                        new AllowlistEntry(
                                "modules/zolt-build/src/main/java/com/zolt/build/Large.java",
                                50,
                                35,
                                12,
                                22,
                                "",
                                "legacy coordinator")),
                readAllowlist(allowlist));
    }

    private static List<Budget> readBudgets(Path path) throws IOException {
        List<Budget> budgets = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            Optional<Budget> budget = parseBudgetLine(line);
            budget.ifPresent(budgets::add);
        }
        return budgets;
    }

    private static Optional<Budget> parseBudgetLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return Optional.empty();
        }
        String[] parts = trimmed.split("\\|");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid complexity budget line: " + line);
        }
        return Optional.of(new Budget(
                Path.of(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]),
                Integer.parseInt(parts[4])));
    }

    private static Map<String, AllowlistEntry> readAllowlist(Path path) throws IOException {
        Map<String, AllowlistEntry> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            Optional<AllowlistEntry> entry = parseAllowlistLine(line);
            if (entry.isEmpty()) {
                continue;
            }
            AllowlistEntry previous = entries.put(entry.orElseThrow().path(), entry.orElseThrow());
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate complexity allowlist entry: " + entry.orElseThrow().path());
            }
        }
        return entries;
    }

    private static Optional<AllowlistEntry> parseAllowlistLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return Optional.empty();
        }
        String[] parts = trimmed.split("\\|", -1);
        if (parts.length != 7) {
            throw new IllegalArgumentException("Invalid complexity allowlist line: " + line);
        }
        return Optional.of(new AllowlistEntry(
                parts[0],
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]),
                Integer.parseInt(parts[4]),
                parts[5],
                parts[6]));
    }

    private static Map<String, SourceComplexity> overBudgetSources(List<Budget> budgets) throws IOException {
        Map<String, SourceComplexity> sources = new LinkedHashMap<>();
        for (Budget budget : budgets) {
            for (SourceComplexity complexity : sourceComplexities(budget)) {
                if (isOverBudget(complexity, budget)) {
                    sources.put(complexity.path(), complexity);
                }
            }
        }
        return sources;
    }

    private static boolean isOverBudget(SourceComplexity complexity, Budget budget) {
        return complexity.imports() > budget.maxImports()
                || complexity.publicMethods() > budget.maxPublicMethods()
                || complexity.constructorParameters() > budget.maxConstructorParameters()
                || complexity.nestedTypes() > budget.maxNestedTypes();
    }

    private static List<SourceComplexity> sourceComplexities(Budget budget) throws IOException {
        List<SourceComplexity> complexities = new ArrayList<>();
        for (Path sourceRoot : sourceRoots(budget.rootPattern())) {
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                for (Path javaFile : paths.filter(path -> path.toString().endsWith(".java"))
                        .sorted()
                        .toList()) {
                    complexities.add(scan(javaFile));
                }
            }
        }
        complexities.sort(Comparator.comparing(SourceComplexity::path));
        return List.copyOf(complexities);
    }

    private static SourceComplexity scan(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        Set<String> imports = new LinkedHashSet<>();
        int publicMethods = 0;
        int maxConstructorParameters = 0;
        int nestedTypes = 0;
        String className = path.getFileName().toString().replaceFirst("\\.java$", "");

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            Matcher importMatcher = IMPORT_PATTERN.matcher(line);
            if (importMatcher.matches()) {
                imports.add(importMatcher.group(1));
            }
            if (PUBLIC_METHOD_PATTERN.matcher(line).find()) {
                publicMethods++;
            }
            if (isConstructorStart(line, className)) {
                String signature = signature(lines, index);
                maxConstructorParameters = Math.max(maxConstructorParameters, parameterCount(signature));
            }
            if (NESTED_TYPE_PATTERN.matcher(line).matches()) {
                nestedTypes++;
            }
        }

        return new SourceComplexity(
                RepositoryPaths.displayPath(path),
                imports.size(),
                publicMethods,
                maxConstructorParameters,
                nestedTypes);
    }

    private static boolean isConstructorStart(String line, String className) {
        return Pattern.compile("^\\s*(?:public|private|protected)?\\s*" + Pattern.quote(className) + "\\s*\\(")
                .matcher(line)
                .find();
    }

    private static String signature(List<String> lines, int start) {
        StringBuilder signature = new StringBuilder();
        for (int index = start; index < lines.size(); index++) {
            signature.append(lines.get(index)).append(' ');
            if (lines.get(index).contains(")")) {
                break;
            }
        }
        return signature.toString();
    }

    private static int parameterCount(String signature) {
        int open = signature.indexOf('(');
        int close = signature.indexOf(')', open + 1);
        if (open < 0 || close < 0) {
            return 0;
        }
        String parameters = signature.substring(open + 1, close).trim();
        if (parameters.isEmpty()) {
            return 0;
        }
        int count = 1;
        int genericDepth = 0;
        for (int index = 0; index < parameters.length(); index++) {
            char character = parameters.charAt(index);
            if (character == '<') {
                genericDepth++;
            } else if (character == '>') {
                genericDepth = Math.max(0, genericDepth - 1);
            } else if (character == ',' && genericDepth == 0) {
                count++;
            }
        }
        return count;
    }

    private static List<Path> sourceRoots(Path rootPattern) throws IOException {
        Path resolvedPattern = resolveRootPattern(rootPattern);
        if (!containsWildcard(resolvedPattern)) {
            return Files.isDirectory(resolvedPattern) ? List.of(resolvedPattern) : List.of();
        }
        Path base = wildcardBase(resolvedPattern);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + resolvedPattern);
        try (Stream<Path> paths = Files.walk(base)) {
            return paths.filter(Files::isDirectory)
                    .map(Path::normalize)
                    .filter(matcher::matches)
                    .sorted()
                    .toList();
        }
    }

    private static Path resolveRootPattern(Path rootPattern) {
        if (rootPattern.isAbsolute()) {
            return rootPattern.normalize();
        }
        return RepositoryPaths.root().resolve(rootPattern).normalize();
    }

    private static boolean containsWildcard(Path path) {
        return path.toString().contains("*");
    }

    private static Path wildcardBase(Path pattern) {
        Path base = pattern.getRoot();
        for (Path part : pattern) {
            if (containsWildcard(part)) {
                break;
            }
            base = base == null ? part : base.resolve(part);
        }
        return base == null ? Path.of(".") : base;
    }

    private static String describe(List<String> values) {
        StringBuilder description = new StringBuilder();
        for (String value : values) {
            description.append("- ").append(value).append('\n');
        }
        return description.toString();
    }

    private record Budget(
            Path rootPattern,
            int maxImports,
            int maxPublicMethods,
            int maxConstructorParameters,
            int maxNestedTypes) {
    }

    private record SourceComplexity(
            String path,
            int imports,
            int publicMethods,
            int constructorParameters,
            int nestedTypes) {
        private String describeMetrics() {
            return "imports="
                    + imports
                    + ", publicMethods="
                    + publicMethods
                    + ", constructorParameters="
                    + constructorParameters
                    + ", nestedTypes="
                    + nestedTypes;
        }
    }

    private record AllowlistEntry(
            String path,
            int maxImports,
            int maxPublicMethods,
            int maxConstructorParameters,
            int maxNestedTypes,
            String followUp,
            String reason) {
    }
}
