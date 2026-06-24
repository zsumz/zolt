package com.zolt.arch;

import static com.zolt.arch.ArchitectureBudgetSupport.sourceRoots;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class ContextFootprintBudgetSupport {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");

    private ContextFootprintBudgetSupport() {}

    static List<Budget> readBudgets(Path path) throws IOException {
        List<Budget> budgets = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            Optional<Budget> budget = parseBudgetLine(line);
            budget.ifPresent(budgets::add);
        }
        return budgets;
    }

    static List<PackageFootprint> packageFootprints(List<Budget> budgets) throws IOException {
        List<PackageFootprint> footprints = new ArrayList<>();
        for (Budget budget : budgets) {
            footprints.addAll(packageFootprints(budget));
        }
        footprints.sort(Comparator.comparing(PackageFootprint::root).thenComparing(PackageFootprint::packageName));
        return List.copyOf(footprints);
    }

    static List<PackageFootprint> packageFootprints(Budget budget) throws IOException {
        Map<PackageKey, PackageFootprintBuilder> footprints = new LinkedHashMap<>();
        for (Path sourceRoot : sourceRoots(budget.rootPattern())) {
            String displayRoot = RepositoryPaths.displayPath(sourceRoot);
            for (Path javaFile : ArchitectureSourceFiles.javaFiles(List.of(sourceRoot))) {
                String packageName = packageName(javaFile);
                PackageKey key = new PackageKey(displayRoot, packageName);
                footprints.computeIfAbsent(key, ignored -> new PackageFootprintBuilder(displayRoot, packageName))
                        .add(lineCount(javaFile));
            }
        }
        return footprints.values().stream()
                .map(PackageFootprintBuilder::build)
                .sorted(Comparator.comparing(PackageFootprint::root).thenComparing(PackageFootprint::packageName))
                .toList();
    }

    static String violation(PackageFootprint footprint, Budget budget) {
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

    static void writeSource(Path path, String packageName, int count) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("package " + packageName + ";");
        for (int index = 1; index < count; index++) {
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
            throw new IllegalArgumentException("Invalid context footprint budget line: " + line);
        }
        return Optional.of(new Budget(
                Path.of(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])));
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

    record Budget(Path rootPattern, int maxFiles, int maxLines) {
    }

    private record PackageKey(String root, String packageName) {
    }

    record PackageFootprint(String root, String packageName, int files, int lines) {
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
