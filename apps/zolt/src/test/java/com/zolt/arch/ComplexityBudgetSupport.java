package com.zolt.arch;

import static com.zolt.arch.ArchitectureBudgetSupport.sourceRoots;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

final class ComplexityBudgetSupport {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+)(?:\\.\\*)?\\s*;");
    private static final Pattern PUBLIC_METHOD_PATTERN = Pattern.compile(
            "^\\s*public\\s+(?:static\\s+|final\\s+|synchronized\\s+|abstract\\s+)*[\\w<>, ?\\[\\].]+\\s+(\\w+)\\s*\\(");
    private static final Pattern NESTED_TYPE_PATTERN = Pattern.compile(
            "^\\s+(?:public\\s+|private\\s+|protected\\s+|static\\s+|final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+)*(?:class|record|enum|interface)\\s+\\w+.*");

    private ComplexityBudgetSupport() {}

    static List<Budget> readBudgets(Path path) throws IOException {
        List<Budget> budgets = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            Optional<Budget> budget = parseBudgetLine(line);
            budget.ifPresent(budgets::add);
        }
        return budgets;
    }

    static Map<String, AllowlistEntry> readAllowlist(Path path) throws IOException {
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

    static Map<String, SourceComplexity> overBudgetSources(List<Budget> budgets) throws IOException {
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

    static SourceComplexity scan(Path path) throws IOException {
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

    static String describe(List<String> values) {
        return ArchitectureBudgetSupport.describe(values);
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

    record Budget(Path rootPattern, int maxImports, int maxPublicMethods, int maxConstructorParameters, int maxNestedTypes) {
    }

    record SourceComplexity(
            String path,
            int imports,
            int publicMethods,
            int constructorParameters,
            int nestedTypes) {
        String describeMetrics() {
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

    record AllowlistEntry(
            String path, int maxImports, int maxPublicMethods, int maxConstructorParameters, int maxNestedTypes, String followUp, String reason) {
    }
}
