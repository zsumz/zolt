package sh.zolt.test;

import sh.zolt.project.ProjectConfig;
import sh.zolt.project.TestSuiteSettings;
import sh.zolt.test.shard.TestShardException;
import sh.zolt.test.shard.TestShardManifestWriter;
import sh.zolt.test.shard.TestShardPlan;
import sh.zolt.test.shard.TestShardPlanner;
import sh.zolt.test.shard.TestShardSpec;
import sh.zolt.test.shard.TestWorkerPoolScheduler;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class TestSuitePlanner {
    private final TestInventoryBuilder inventoryBuilder;
    private final TestShardPlanner shardPlanner = new TestShardPlanner();
    private final TestShardManifestWriter shardManifestWriter = new TestShardManifestWriter();
    private final TestWorkerPoolScheduler workerPoolScheduler = new TestWorkerPoolScheduler();

    public TestSuitePlanner() {
        this(new TestInventoryBuilder());
    }

    TestSuitePlanner(TestInventoryBuilder inventoryBuilder) {
        this.inventoryBuilder = inventoryBuilder;
    }

    public TestSuitePlan plan(
            Path projectDirectory,
            ProjectConfig config,
            String requestedSuite,
            TestSelection selection) {
        String suiteName = requestedSuite == null || requestedSuite.isBlank() ? "all" : requestedSuite;
        TestSelection testSelection = selection == null ? TestSelection.empty() : selection;
        Path outputDirectory = projectDirectory.resolve(config.build().testOutput());
        TestInventory allInventory = inventoryBuilder.scanAll(outputDirectory);
        List<TestInventoryEntry> entries;
        TestSuiteSettings suiteSettings = null;
        boolean configuredSuite = !"all".equals(suiteName);
        if ("all".equals(suiteName)) {
            entries = inventoryBuilder.scan(outputDirectory, testSelection).entries();
        } else {
            suiteSettings = config.build().testSuites().get(suiteName);
            if (suiteSettings == null) {
                throw new TestPlanException(
                        "Unknown test suite `"
                                + suiteName
                                + "`. Add [test.suites."
                                + suiteName
                                + "] to zolt.toml or use a configured suite.");
            }
            entries = applySelection(
                    applySuiteClassFilters(allInventory.entries(), suiteSettings),
                    testSelection);
        }
        return new TestSuitePlan(
                suiteName,
                configuredSuite,
                outputDirectory,
                entries,
                suiteSettings == null ? List.of() : suiteSettings.includeClassname(),
                suiteSettings == null ? List.of() : suiteSettings.excludeClassname(),
                suiteSettings == null ? List.of() : suiteSettings.includeTag(),
                suiteSettings == null ? List.of() : suiteSettings.excludeTag(),
                selectionClassname(testSelection),
                testSelection.includedTags(),
                testSelection.excludedTags(),
                missingExplicitClasses(allInventory.entries(), explicitClasses(testSelection)),
                overlappingEntries(entries, config.build().testSuites(), suiteName),
                unassignedEntries(allInventory.entries(), config.build().testSuites()));
    }

    public TestSelection executionSelection(
            Path projectDirectory,
            ProjectConfig config,
            String requestedSuite,
            TestSelection selection) {
        return executionSelection(projectDirectory, config, requestedSuite, selection, null);
    }

    public TestSelection executionSelection(
            Path projectDirectory,
            ProjectConfig config,
            String requestedSuite,
            TestSelection selection,
            TestShardSpec shard) {
        return executionPlan(projectDirectory, config, requestedSuite, selection, shard).selection();
    }

    public TestSuiteExecutionPlan executionPlan(
            Path projectDirectory,
            ProjectConfig config,
            String requestedSuite,
            TestSelection selection,
            TestShardSpec shard) {
        String suiteName = requestedSuite == null || requestedSuite.isBlank() ? "all" : requestedSuite;
        TestSelection testSelection = selection == null ? TestSelection.empty() : selection;
        if ("all".equals(suiteName) && shard == null) {
            return new TestSuiteExecutionPlan(
                    testSelection,
                    workerPoolScheduler.plan(List.of(), TestSuiteSettings.empty()));
        }
        TestSuitePlan plan = plan(projectDirectory, config, suiteName, testSelection);
        TestSuiteSettings suiteSettings = "all".equals(suiteName)
                ? TestSuiteSettings.empty()
                : config.build().testSuites().get(suiteName);
        if (shard != null) {
            TestShardPlan shardPlan = shardPlanner.plan(projectDirectory, config, plan, shard);
            shardManifestWriter.write(shardPlan);
            if (shardPlan.empty()) {
                throw new TestPlanException(
                        "Test shard `"
                                + shard.label()
                                + "` for suite `"
                                + suiteName
                                + "` did not match any compiled test classes. Wrote shard manifest to "
                                + shardPlan.projectRelativeManifestPath(projectDirectory)
                                + ".");
            }
            return new TestSuiteExecutionPlan(
                    executionSelection(plan, shardPlan.entries(), testSelection),
                    workerPoolScheduler.plan(shardPlan.entries(), suiteSettings));
        }
        if (plan.empty()) {
            throw new TestPlanException(
                    "Test suite `"
                            + suiteName
                            + "` did not match any compiled test classes. "
                            + "Run `zolt test plan --suite "
                            + suiteName
                            + "` to inspect suite membership.");
        }
        return new TestSuiteExecutionPlan(
                executionSelection(plan, plan.entries(), testSelection),
                workerPoolScheduler.plan(plan.entries(), suiteSettings));
    }

    public List<TestShardPlan> shardPlans(
            Path projectDirectory,
            ProjectConfig config,
            String requestedSuite,
            TestSelection selection,
            int shardCount) {
        return shardPlans(projectDirectory, config, requestedSuite, selection, shardCount, TestProfileHistory.none());
    }

    public List<TestShardPlan> shardPlans(
            Path projectDirectory,
            ProjectConfig config,
            String requestedSuite,
            TestSelection selection,
            int shardCount,
            TestProfileHistory profileHistory) {
        if (shardCount < 1) {
            throw new TestShardException("Invalid --shard-count `" + shardCount + "`. Use a positive integer.");
        }
        TestSuitePlan plan = plan(projectDirectory, config, requestedSuite, selection);
        return shardPlanner.plans(projectDirectory, config, plan, shardCount, profileHistory);
    }

    private static TestSelection executionSelection(
            TestSuitePlan plan,
            List<TestInventoryEntry> entries,
            TestSelection testSelection) {
        LinkedHashSet<String> plannedClasses = entries.stream()
                .map(TestInventoryEntry::className)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        boolean hasExplicitClassOrMethodSelection =
                !testSelection.classSelectors().isEmpty() || !testSelection.methodSelectors().isEmpty();
        List<String> classSelectors = hasExplicitClassOrMethodSelection
                ? testSelection.classSelectors().stream()
                        .filter(plannedClasses::contains)
                        .toList()
                : List.copyOf(plannedClasses);
        List<TestSelection.MethodSelector> methodSelectors = testSelection.methodSelectors().stream()
                .filter(method -> plannedClasses.contains(method.className()))
                .toList();
        return TestSelection.fromFields(
                classSelectors,
                methodSelectors,
                List.of(),
                combined(plan.includeTag(), testSelection.includedTags()),
                combined(plan.excludeTag(), testSelection.excludedTags()));
    }

    private static List<TestInventoryEntry> applySuiteClassFilters(
            List<TestInventoryEntry> entries,
            TestSuiteSettings suite) {
        List<Pattern> includePatterns = patterns(suite.includeClassname());
        List<Pattern> excludePatterns = patterns(suite.excludeClassname());
        return entries.stream()
                .filter(entry -> includePatterns.isEmpty() || matches(entry.className(), includePatterns))
                .filter(entry -> !matches(entry.className(), excludePatterns))
                .map(entry -> withMatchedPatterns(entry, suite.includeClassname()))
                .toList();
    }

    private static List<TestInventoryEntry> applySelection(
            List<TestInventoryEntry> entries,
            TestSelection selection) {
        List<String> explicitClasses = explicitClasses(selection);
        List<Pattern> classNamePatterns = patterns(selection.classNamePatterns());
        return entries.stream()
                .filter(entry -> explicitClasses.isEmpty() || explicitClasses.contains(entry.className()))
                .filter(entry -> classNamePatterns.isEmpty() || matches(entry.className(), classNamePatterns))
                .toList();
    }

    private static TestInventoryEntry withMatchedPatterns(TestInventoryEntry entry, List<String> patterns) {
        List<String> regexes = patterns.stream()
                .map(TestSelection::toClassNameRegex)
                .toList();
        List<String> matched = new ArrayList<>();
        for (int index = 0; index < regexes.size(); index++) {
            if (Pattern.compile(regexes.get(index)).matcher(entry.className()).matches()) {
                matched.add(patterns.get(index));
            }
        }
        return new TestInventoryEntry(
                entry.className(),
                entry.outputRoot(),
                entry.classFile(),
                matched,
                entry.engineId(),
                entry.tags());
    }

    private static Map<String, List<String>> overlappingEntries(
            List<TestInventoryEntry> entries,
            Map<String, TestSuiteSettings> suites,
            String selectedSuite) {
        Map<String, List<String>> overlaps = new LinkedHashMap<>();
        if (suites.isEmpty()) {
            return overlaps;
        }
        for (TestInventoryEntry entry : entries) {
            List<String> matchingSuites = suites.entrySet().stream()
                    .filter(suite -> selectedSuite == null
                            || "all".equals(selectedSuite)
                            || !suite.getKey().equals(selectedSuite))
                    .filter(suite -> suiteMatches(entry, suite.getValue()))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            if (matchingSuites.size() > ("all".equals(selectedSuite) ? 1 : 0)) {
                overlaps.put(entry.className(), matchingSuites);
            }
        }
        return overlaps;
    }

    private static List<String> unassignedEntries(
            List<TestInventoryEntry> entries,
            Map<String, TestSuiteSettings> suites) {
        if (suites.isEmpty()) {
            return List.of();
        }
        return entries.stream()
                .filter(entry -> suites.values().stream().noneMatch(suite -> suiteMatches(entry, suite)))
                .map(TestInventoryEntry::className)
                .sorted()
                .toList();
    }

    private static boolean suiteMatches(TestInventoryEntry entry, TestSuiteSettings suite) {
        List<Pattern> includes = patterns(suite.includeClassname());
        List<Pattern> excludes = patterns(suite.excludeClassname());
        return (includes.isEmpty() || matches(entry.className(), includes))
                && !matches(entry.className(), excludes);
    }

    private static List<Pattern> patterns(List<String> patterns) {
        return patterns.stream()
                .map(TestSelection::toClassNameRegex)
                .map(Pattern::compile)
                .toList();
    }

    private static boolean matches(String className, List<Pattern> patterns) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(className).matches());
    }

    private static List<String> selectionClassname(TestSelection selection) {
        List<String> values = new ArrayList<>(selection.classSelectors());
        selection.methodSelectors().stream()
                .map(method -> method.className() + "#" + method.methodName())
                .forEach(values::add);
        values.addAll(selection.classNamePatterns());
        return List.copyOf(values);
    }

    private static List<String> explicitClasses(TestSelection selection) {
        Set<String> classes = new LinkedHashSet<>(selection.classSelectors());
        for (TestSelection.MethodSelector method : selection.methodSelectors()) {
            classes.add(method.className());
        }
        return List.copyOf(classes);
    }

    private static List<String> missingExplicitClasses(
            List<TestInventoryEntry> entries,
            List<String> explicitClasses) {
        Set<String> discovered = entries.stream()
                .map(TestInventoryEntry::className)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return explicitClasses.stream()
                .filter(className -> !discovered.contains(className))
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static List<String> combined(List<String> first, List<String> second) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(first);
        values.addAll(second);
        return List.copyOf(values);
    }
}
