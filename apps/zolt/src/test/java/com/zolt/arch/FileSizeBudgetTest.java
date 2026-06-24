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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileSizeBudgetTest {
    private static final Path ALLOWLIST =
            RepositoryPaths.appRoot().resolve("src/test/resources/com/zolt/arch/file-size-allowlist.txt");
    private static final Path BUDGETS =
            RepositoryPaths.appRoot().resolve("src/test/resources/com/zolt/arch/file-size-budgets.txt");

    @Test
    void javaFilesStayBelowSoftThresholds() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Budget budget : readBudgets()) {
            for (SourceFileSize fileSize : sourceFileSizes(budget)) {
                if (fileSize.lines() > budget.softThreshold()) {
                    violations.add(fileSize.path()
                            + " has "
                            + fileSize.lines()
                            + " lines and exceeds the soft threshold of "
                            + budget.softThreshold()
                            + " lines");
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                () -> "File-size soft threshold violations:\n"
                        + describe(violations)
                        + "\nSplit the file or update `docs/code-organization.md` with a planned policy change.");
    }

    @Test
    void hardThresholdFilesAreExplicitlyAllowlistedAndDoNotGrow() throws IOException {
        Map<String, AllowlistEntry> allowlist = readAllowlist();
        Map<String, SourceFileSize> oversizedFiles = filesAboveHardThreshold(readBudgets());
        List<String> violations = new ArrayList<>();

        for (SourceFileSize fileSize : oversizedFiles.values()) {
            AllowlistEntry entry = allowlist.get(fileSize.path());
            if (entry == null) {
                violations.add(fileSize.path()
                        + " has "
                        + fileSize.lines()
                        + " lines and needs a planned allowlist entry");
            } else if (fileSize.lines() > entry.maxLines()) {
                violations.add(fileSize.path()
                        + " grew from allowlisted max "
                        + entry.maxLines()
                        + " to "
                        + fileSize.lines()
                        + " lines ["
                        + entry.followUp()
                        + "]");
            }
        }

        List<String> staleEntries = allowlist.keySet().stream()
                .filter(path -> !oversizedFiles.containsKey(path))
                .sorted()
                .toList();
        for (String staleEntry : staleEntries) {
            violations.add(staleEntry + " is no longer above the hard threshold; remove the allowlist entry");
        }

        assertTrue(
                violations.isEmpty(),
                () -> "File-size budget violations:\n"
                        + describe(violations)
                        + "\nRun `scripts/report-file-size-budgets` for the current soft-threshold report.");
    }

    @Test
    void scannerFindsFilesAboveHardThreshold(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java");
        writeLines(sourceRoot.resolve("Small.java"), 3);
        writeLines(sourceRoot.resolve("Large.java"), 6);

        assertEquals(
                Map.of(
                        RepositoryPaths.displayPath(sourceRoot.resolve("Large.java")),
                        new SourceFileSize(RepositoryPaths.displayPath(sourceRoot.resolve("Large.java")), 6)),
                filesAboveHardThreshold(List.of(new Budget(sourceRoot, 4, 5))));
    }

    @Test
    void scannerFindsFilesAboveSoftThreshold(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/test/java");
        writeLines(sourceRoot.resolve("SmallTest.java"), 3);
        writeLines(sourceRoot.resolve("LargeTest.java"), 5);

        assertEquals(
                Map.of(
                        RepositoryPaths.displayPath(sourceRoot.resolve("LargeTest.java")),
                        new SourceFileSize(RepositoryPaths.displayPath(sourceRoot.resolve("LargeTest.java")), 5)),
                filesAboveSoftThreshold(List.of(new Budget(sourceRoot, 4, 10))));
    }

    @Test
    void scannerExpandsWildcardRootPatterns(@TempDir Path tempDir) throws IOException {
        Path alphaSourceRoot = tempDir.resolve("modules/alpha/src/main/java");
        Path betaSourceRoot = tempDir.resolve("modules/beta/src/main/java");
        writeLines(alphaSourceRoot.resolve("Small.java"), 3);
        writeLines(betaSourceRoot.resolve("Large.java"), 5);

        assertEquals(
                Map.of(
                        RepositoryPaths.displayPath(betaSourceRoot.resolve("Large.java")),
                        new SourceFileSize(RepositoryPaths.displayPath(betaSourceRoot.resolve("Large.java")), 5)),
                filesAboveSoftThreshold(List.of(new Budget(
                        tempDir.resolve("modules/*/src/main/java"),
                        4,
                        10))));
    }

    @Test
    void budgetFileParserReadsRootsAndThresholds(@TempDir Path tempDir) throws IOException {
        Path budgets = tempDir.resolve("budgets.txt");
        Files.writeString(budgets, """
                # rootPattern|softThreshold|hardThreshold
                apps/*/src/main/java|350|500

                modules/*/src/test/java|450|650
                """);

        assertEquals(
                List.of(
                        new Budget(Path.of("apps/*/src/main/java"), 350, 500),
                        new Budget(Path.of("modules/*/src/test/java"), 450, 650)),
                readBudgets(budgets));
    }

    @Test
    void reportScriptMarksEmptyBudgetSections() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("scripts/report-file-size-budgets")
                .directory(RepositoryPaths.root().toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());

        assertEquals(0, process.waitFor(), output);
        assertTrue(output.contains("Production Java files over budget in apps/*/src/main/java"));
        assertEquals(4, output.lines().filter("none"::equals).count(), output);
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
            throw new IllegalArgumentException("Invalid file-size budget line: " + line);
        }
        return Optional.of(new Budget(
                Path.of(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])));
    }

    private static Map<String, SourceFileSize> filesAboveSoftThreshold(List<Budget> budgets) throws IOException {
        Map<String, SourceFileSize> files = new LinkedHashMap<>();
        for (Budget budget : budgets) {
            for (SourceFileSize fileSize : sourceFileSizes(budget)) {
                if (fileSize.lines() > budget.softThreshold()) {
                    files.put(fileSize.path(), fileSize);
                }
            }
        }
        return files;
    }

    private static Map<String, SourceFileSize> filesAboveHardThreshold(List<Budget> budgets) throws IOException {
        Map<String, SourceFileSize> files = new LinkedHashMap<>();
        for (Budget budget : budgets) {
            for (SourceFileSize fileSize : sourceFileSizes(budget)) {
                if (fileSize.lines() > budget.hardThreshold()) {
                    files.put(fileSize.path(), fileSize);
                }
            }
        }
        return files;
    }

    private static List<SourceFileSize> sourceFileSizes(Budget budget) throws IOException {
        List<SourceFileSize> fileSizes = new ArrayList<>();
        for (Path sourceRoot : sourceRoots(budget.rootPattern())) {
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .sorted()
                        .map(path -> new SourceFileSize(RepositoryPaths.displayPath(path), lineCount(path)))
                        .forEach(fileSizes::add);
            }
        }
        fileSizes.sort(Comparator.comparing(SourceFileSize::path));
        return List.copyOf(fileSizes);
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
            return paths
                    .filter(Files::isDirectory)
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

    private static Map<String, AllowlistEntry> readAllowlist() throws IOException {
        Map<String, AllowlistEntry> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(ALLOWLIST)) {
            Optional<AllowlistEntry> entry = parseAllowlistLine(line);
            if (entry.isEmpty()) {
                continue;
            }
            AllowlistEntry previous = entries.put(entry.orElseThrow().path(), entry.orElseThrow());
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate file-size allowlist entry: " + entry.orElseThrow().path());
            }
        }
        return entries;
    }

    private static Optional<AllowlistEntry> parseAllowlistLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return Optional.empty();
        }
        String[] parts = trimmed.split("\\|");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid file-size allowlist line: " + line);
        }
        return Optional.of(new AllowlistEntry(parts[0], Integer.parseInt(parts[1]), parts[2]));
    }

    private static int lineCount(Path path) {
        try (Stream<String> lines = Files.lines(path)) {
            return Math.toIntExact(lines.count());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not count lines in " + path, exception);
        }
    }

    private static void writeLines(Path path, int count) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < count; index++) {
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

    private record Budget(Path rootPattern, int softThreshold, int hardThreshold) {
    }

    private record SourceFileSize(String path, int lines) {
    }

    private record AllowlistEntry(String path, int maxLines, String followUp) {
    }
}
