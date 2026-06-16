package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final Path ALLOWLIST = Path.of("src/test/resources/com/zolt/arch/file-size-allowlist.txt");
    private static final List<Budget> BUDGETS = List.of(
            new Budget(Path.of("src/main/java"), 350, 500),
            new Budget(Path.of("src/test/java"), 450, 650));

    @Test
    void javaFilesStayBelowSoftThresholds() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Budget budget : BUDGETS) {
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
        Map<String, SourceFileSize> oversizedFiles = filesAboveHardThreshold(BUDGETS);
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
                Map.of("src/main/java/Large.java", new SourceFileSize("src/main/java/Large.java", 6)),
                filesAboveHardThreshold(List.of(new Budget(sourceRoot, 4, 5))));
    }

    @Test
    void scannerFindsFilesAboveSoftThreshold(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/test/java");
        writeLines(sourceRoot.resolve("SmallTest.java"), 3);
        writeLines(sourceRoot.resolve("LargeTest.java"), 5);

        assertEquals(
                Map.of("src/test/java/LargeTest.java", new SourceFileSize("src/test/java/LargeTest.java", 5)),
                filesAboveSoftThreshold(List.of(new Budget(sourceRoot, 4, 10))));
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
        if (!Files.isDirectory(budget.root())) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(budget.root())) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .map(path -> new SourceFileSize(displayPath(path), lineCount(path)))
                    .sorted(Comparator.comparing(SourceFileSize::path))
                    .toList();
        }
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

    private static String displayPath(Path path) {
        String value = path.normalize().toString().replace('\\', '/');
        int sourceIndex = value.indexOf("src/");
        return sourceIndex >= 0 ? value.substring(sourceIndex) : value;
    }

    private static String describe(List<String> values) {
        StringBuilder description = new StringBuilder();
        for (String value : values) {
            description.append("- ").append(value).append('\n');
        }
        return description.toString();
    }

    private record Budget(Path root, int softThreshold, int hardThreshold) {
    }

    private record SourceFileSize(String path, int lines) {
    }

    private record AllowlistEntry(String path, int maxLines, String followUp) {
    }
}
