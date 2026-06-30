package com.zolt.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class TestInventoryBuilder {
    public TestInventory scan(Path testOutputDirectory, TestSelection selection) {
        return scan(List.of(testOutputDirectory), selection);
    }

    public TestInventory scan(List<Path> testOutputDirectories, TestSelection selection) {
        TestSelection testSelection = selection == null ? TestSelection.empty() : selection;
        List<Path> outputRoots = normalizeRoots(testOutputDirectories);
        List<TestInventoryEntry> discoveredEntries = discover(outputRoots);
        List<String> explicitClasses = explicitClasses(testSelection);
        List<String> appliedPatterns = appliedClassNamePatterns(testSelection, explicitClasses.isEmpty());
        List<Pattern> compiledPatterns = appliedPatterns.stream()
                .map(Pattern::compile)
                .toList();
        List<TestInventoryEntry> selectedEntries = selectedEntries(
                discoveredEntries,
                explicitClasses,
                compiledPatterns,
                appliedPatterns);
        TestInventorySummary summary = new TestInventorySummary(
                selectedEntries.size(),
                outputRoots,
                testSelection.classSelectors(),
                methodSelectors(testSelection),
                appliedPatterns,
                testSelection.includedTags(),
                testSelection.excludedTags(),
                missingExplicitClasses(discoveredEntries, explicitClasses));
        return new TestInventory(selectedEntries, summary);
    }

    public TestInventory scanAll(Path testOutputDirectory) {
        return scanAll(List.of(testOutputDirectory));
    }

    public TestInventory scanAll(List<Path> testOutputDirectories) {
        List<Path> outputRoots = normalizeRoots(testOutputDirectories);
        List<TestInventoryEntry> entries = discover(outputRoots);
        return new TestInventory(
                entries,
                new TestInventorySummary(
                        entries.size(),
                        outputRoots,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
    }

    private static List<Path> normalizeRoots(List<Path> roots) {
        List<Path> normalized = new ArrayList<>();
        for (Path root : roots == null ? List.<Path>of() : roots) {
            if (root != null) {
                normalized.add(root.toAbsolutePath().normalize());
            }
        }
        return List.copyOf(normalized);
    }

    private static List<TestInventoryEntry> discover(List<Path> outputRoots) {
        List<TestInventoryEntry> entries = new ArrayList<>();
        for (Path outputRoot : outputRoots) {
            if (!Files.isDirectory(outputRoot)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(outputRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(TestInventoryBuilder::isClassFile)
                        .map(path -> entry(outputRoot, path))
                        .forEach(entries::add);
            } catch (IOException exception) {
                throw new UncheckedIOException(
                        "Could not scan compiled test classes under " + outputRoot + ".", exception);
            }
        }
        return entries.stream()
                .sorted(Comparator.comparing(TestInventoryEntry::className)
                        .thenComparing(entry -> entry.classFile().toString()))
                .toList();
    }

    private static boolean isClassFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".class")
                && !fileName.equals("module-info.class")
                && !fileName.equals("package-info.class");
    }

    private static TestInventoryEntry entry(Path outputRoot, Path classFile) {
        return new TestInventoryEntry(
                className(outputRoot, classFile),
                outputRoot,
                classFile.toAbsolutePath().normalize(),
                List.of(),
                "",
                List.of());
    }

    private static String className(Path outputRoot, Path classFile) {
        Path relative = outputRoot.relativize(classFile.toAbsolutePath().normalize());
        List<String> parts = new ArrayList<>();
        for (Path part : relative) {
            parts.add(part.toString());
        }
        int last = parts.size() - 1;
        String fileName = parts.get(last);
        parts.set(last, fileName.substring(0, fileName.length() - ".class".length()));
        return String.join(".", parts);
    }

    private static List<String> explicitClasses(TestSelection selection) {
        LinkedHashSet<String> classes = new LinkedHashSet<>(selection.classSelectors());
        for (TestSelection.MethodSelector methodSelector : selection.methodSelectors()) {
            classes.add(methodSelector.className());
        }
        return List.copyOf(classes);
    }

    private static List<String> appliedClassNamePatterns(TestSelection selection, boolean scanClassPath) {
        if (selection.classNamePatterns().isEmpty()) {
            return scanClassPath ? TestSelection.defaultScanClassNamePatterns() : List.of();
        }
        return selection.classNameRegexPatterns();
    }

    private static List<TestInventoryEntry> selectedEntries(
            List<TestInventoryEntry> entries,
            List<String> explicitClasses,
            List<Pattern> compiledPatterns,
            List<String> appliedPatterns) {
        if (explicitClasses.isEmpty()) {
            return entries.stream()
                    .filter(entry -> matchesPatterns(entry.className(), compiledPatterns))
                    .map(entry -> withPatterns(entry, appliedPatterns))
                    .toList();
        }
        Set<String> explicitClassSet = new LinkedHashSet<>(explicitClasses);
        return explicitClasses.stream()
                .flatMap(className -> entries.stream()
                        .filter(entry -> entry.className().equals(className)))
                .filter(entry -> explicitClassSet.contains(entry.className()))
                .filter(entry -> matchesPatterns(entry.className(), compiledPatterns))
                .map(entry -> withPatterns(entry, appliedPatterns))
                .toList();
    }

    private static TestInventoryEntry withPatterns(TestInventoryEntry entry, List<String> appliedPatterns) {
        List<String> matchedPatterns = appliedPatterns.stream()
                .filter(pattern -> Pattern.compile(pattern).matcher(entry.className()).matches())
                .toList();
        return new TestInventoryEntry(
                entry.className(),
                entry.outputRoot(),
                entry.classFile(),
                matchedPatterns,
                entry.engineId(),
                entry.tags());
    }

    private static boolean matchesPatterns(String className, List<Pattern> patterns) {
        if (patterns.isEmpty()) {
            return true;
        }
        return patterns.stream().anyMatch(pattern -> pattern.matcher(className).matches());
    }

    private static List<String> methodSelectors(TestSelection selection) {
        return selection.methodSelectors().stream()
                .map(method -> method.className() + "#" + method.methodName())
                .toList();
    }

    private static List<String> missingExplicitClasses(
            List<TestInventoryEntry> entries,
            List<String> explicitClasses) {
        Set<String> discoveredClasses = new LinkedHashSet<>();
        for (TestInventoryEntry entry : entries) {
            discoveredClasses.add(entry.className());
        }
        return explicitClasses.stream()
                .filter(className -> !discoveredClasses.contains(className))
                .toList();
    }
}
