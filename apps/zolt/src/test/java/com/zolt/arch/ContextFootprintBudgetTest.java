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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ContextFootprintBudgetTest {
    private static final Path BUDGETS =
            RepositoryPaths.appRoot().resolve("src/test/resources/com/zolt/arch/context-footprint-budgets.txt");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");

    @Test
    void packageFootprintsStayWithinBudgets() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Budget budget : readBudgets()) {
            for (PackageFootprint footprint : packageFootprints(budget)) {
                if (footprint.files() > budget.maxFiles() || footprint.lines() > budget.maxLines()) {
                    violations.add(footprint.root()
                            + " "
                            + footprint.packageName()
                            + " has "
                            + footprint.files()
                            + " files and "
                            + footprint.lines()
                            + " lines; budget is "
                            + budget.maxFiles()
                            + " files and "
                            + budget.maxLines()
                            + " lines");
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                () -> "Context footprint budget violations:\n"
                        + describe(violations)
                        + "\nRun `scripts/report-context-footprint` and split the package/root or update the budget with a planned policy change.");
    }

    @Test
    void scannerGroupsFilesByRootAndPackage(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java");
        writeSource(sourceRoot.resolve("com/example/alpha/First.java"), "com.example.alpha", 3);
        writeSource(sourceRoot.resolve("com/example/alpha/Second.java"), "com.example.alpha", 5);
        writeSource(sourceRoot.resolve("com/example/beta/Beta.java"), "com.example.beta", 7);

        assertEquals(
                List.of(
                        new PackageFootprint(RepositoryPaths.displayPath(sourceRoot), "com.example.alpha", 2, 8),
                        new PackageFootprint(RepositoryPaths.displayPath(sourceRoot), "com.example.beta", 1, 7)),
                packageFootprints(List.of(new Budget(sourceRoot, 10, 20))));
    }

    @Test
    void scannerExpandsWildcardRootPatterns(@TempDir Path tempDir) throws IOException {
        Path alphaRoot = tempDir.resolve("modules/alpha/src/main/java");
        Path betaRoot = tempDir.resolve("modules/beta/src/main/java");
        writeSource(alphaRoot.resolve("com/example/Alpha.java"), "com.example.alpha", 3);
        writeSource(betaRoot.resolve("com/example/Beta.java"), "com.example.beta", 5);

        assertEquals(
                List.of(
                        new PackageFootprint(RepositoryPaths.displayPath(alphaRoot), "com.example.alpha", 1, 3),
                        new PackageFootprint(RepositoryPaths.displayPath(betaRoot), "com.example.beta", 1, 5)),
                packageFootprints(List.of(new Budget(
                        tempDir.resolve("modules/*/src/main/java"),
                        10,
                        20))));
    }

    @Test
    void budgetFileParserReadsRootsAndThresholds(@TempDir Path tempDir) throws IOException {
        Path budgets = tempDir.resolve("budgets.txt");
        Files.writeString(budgets, """
                # rootPattern|maxFiles|maxLines
                apps/*/src/main/java|140|15000

                modules/*/src/test/java|120|12000
                """);

        assertEquals(
                List.of(
                        new Budget(Path.of("apps/*/src/main/java"), 140, 15000),
                        new Budget(Path.of("modules/*/src/test/java"), 120, 12000)),
                readBudgets(budgets));
    }

    @Test
    void violationsReportFileAndLineBudgets() {
        PackageFootprint footprint = new PackageFootprint("apps/zolt/src/test/java", "com.zolt.cli", 141, 15001);
        Budget budget = new Budget(Path.of("apps/*/src/test/java"), 140, 15000);

        assertEquals(
                "apps/zolt/src/test/java com.zolt.cli has 141 files and 15001 lines; budget is 140 files and 15000 lines",
                violation(footprint, budget));
    }

    private static List<Budget> readBudgets() throws IOException {
        return readBudgets(BUDGETS);
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
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid context footprint budget line: " + line);
        }
        return Optional.of(new Budget(
                Path.of(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])));
    }

    private static List<PackageFootprint> packageFootprints(List<Budget> budgets) throws IOException {
        List<PackageFootprint> footprints = new ArrayList<>();
        for (Budget budget : budgets) {
            footprints.addAll(packageFootprints(budget));
        }
        footprints.sort(Comparator.comparing(PackageFootprint::root).thenComparing(PackageFootprint::packageName));
        return List.copyOf(footprints);
    }

    private static List<PackageFootprint> packageFootprints(Budget budget) throws IOException {
        Map<PackageKey, PackageFootprintBuilder> footprints = new LinkedHashMap<>();
        for (Path sourceRoot : sourceRoots(budget.rootPattern())) {
            String displayRoot = RepositoryPaths.displayPath(sourceRoot);
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                for (Path javaFile : paths.filter(path -> path.toString().endsWith(".java"))
                        .sorted()
                        .toList()) {
                    String packageName = packageName(javaFile);
                    PackageKey key = new PackageKey(displayRoot, packageName);
                    footprints.computeIfAbsent(key, ignored -> new PackageFootprintBuilder(displayRoot, packageName))
                            .add(lineCount(javaFile));
                }
            }
        }
        return footprints.values().stream()
                .map(PackageFootprintBuilder::build)
                .sorted(Comparator.comparing(PackageFootprint::root).thenComparing(PackageFootprint::packageName))
                .toList();
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

    private static String packageName(Path path) throws IOException {
        for (String line : Files.readAllLines(path)) {
            Matcher matcher = PACKAGE_PATTERN.matcher(line);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return "(default)";
    }

    private static int lineCount(Path path) {
        try (Stream<String> lines = Files.lines(path)) {
            return Math.toIntExact(lines.count());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not count lines in " + path, exception);
        }
    }

    private static String violation(PackageFootprint footprint, Budget budget) {
        return footprint.root()
                + " "
                + footprint.packageName()
                + " has "
                + footprint.files()
                + " files and "
                + footprint.lines()
                + " lines; budget is "
                + budget.maxFiles()
                + " files and "
                + budget.maxLines()
                + " lines";
    }

    private static void writeSource(Path path, String packageName, int count) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("package " + packageName + ";");
        for (int index = 1; index < count; index++) {
            lines.add("// line " + index);
        }
        Files.write(path, lines);
    }

    private static String describe(List<String> values) {
        StringBuilder description = new StringBuilder();
        for (String value : values) {
            description.append("- ").append(value).append('\n');
        }
        return description.toString();
    }

    private record Budget(Path rootPattern, int maxFiles, int maxLines) {
    }

    private record PackageKey(String root, String packageName) {
    }

    private record PackageFootprint(String root, String packageName, int files, int lines) {
    }

    private static final class PackageFootprintBuilder {
        private final String root;
        private final String packageName;
        private int files;
        private int lines;

        private PackageFootprintBuilder(String root, String packageName) {
            this.root = root;
            this.packageName = packageName;
        }

        private void add(int lineCount) {
            files++;
            lines += lineCount;
        }

        private PackageFootprint build() {
            return new PackageFootprint(root, packageName, files, lines);
        }
    }
}
