package com.zolt.arch;

import static com.zolt.arch.ArchitectureBudgetSupport.sourceRoots;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

final class FileSizeBudgetSupport {
    private FileSizeBudgetSupport() {}

    static List<Budget> readBudgets(Path path) throws IOException {
        List<Budget> budgets = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            Optional<Budget> budget = parseBudgetLine(line);
            budget.ifPresent(budgets::add);
        }
        return budgets;
    }

    static Map<String, AllowlistEntry> readAllowlist(Path path) throws IOException {
        return ArchitectureAllowlistSupport.readAllowlist(
                path,
                FileSizeBudgetSupport::parseAllowlistLine,
                AllowlistEntry::path,
                "Duplicate file-size allowlist entry: ");
    }

    static Map<String, SourceFileSize> filesAboveSoftThreshold(List<Budget> budgets) throws IOException {
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

    static Map<String, SourceFileSize> filesAboveHardThreshold(List<Budget> budgets) throws IOException {
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

    static List<SourceFileSize> sourceFileSizes(Budget budget) throws IOException {
        List<SourceFileSize> fileSizes = new ArrayList<>();
        for (Path javaFile : ArchitectureSourceFiles.javaFiles(sourceRoots(budget.rootPattern()))) {
            fileSizes.add(new SourceFileSize(RepositoryPaths.displayPath(javaFile), lineCount(javaFile)));
        }
        return List.copyOf(fileSizes);
    }

    static void writeLines(Path path, int count) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            lines.add("// line " + index);
        }
        Files.write(path, lines);
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

    record Budget(Path rootPattern, int softThreshold, int hardThreshold) {
    }

    record SourceFileSize(String path, int lines) {
    }

    record AllowlistEntry(String path, int maxLines, String followUp) {
    }
}
