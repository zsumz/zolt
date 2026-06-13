package com.zolt.cli.command;

import com.zolt.build.BuildResult;
import com.zolt.build.CompileDiagnostics;
import com.zolt.build.TestCompileResult;
import com.zolt.build.TestRunResult;
import com.zolt.build.TestSelection;
import com.zolt.workspace.WorkspaceTestResult;
import java.util.LinkedHashMap;
import java.util.Map;

final class CommandTestAttributes {
    private CommandTestAttributes() {
    }

    static Map<String, String> testRun(TestRunResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MAIN_SOURCE_FILES, Integer.toString(result.compileResult().buildResult().sourceCount()));
        attributes.put(TimingAttributeKeys.TEST_SOURCE_FILES, Integer.toString(result.compileResult().sourceCount()));
        attributes.put(TimingAttributeKeys.TEST_RESOURCE_FILES, Integer.toString(result.compileResult().resourceCount()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_SKIPPED, Boolean.toString(result.compileResult().buildResult().mainCompilationSkipped()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_MODE, result.compileResult().buildResult().mainCompilationMode());
        attributes.put(TimingAttributeKeys.MAIN_INCREMENTAL_FALLBACK_REASON, result.compileResult().buildResult().mainIncrementalFallbackReason());
        attributes.put(TimingAttributeKeys.TEST_COMPILATION_SKIPPED, Boolean.toString(result.compileResult().testCompilationSkipped()));
        attributes.put(TimingAttributeKeys.TEST_COMPILATION_MODE, result.compileResult().testCompilationMode());
        attributes.put(TimingAttributeKeys.TEST_INCREMENTAL_FALLBACK_REASON, result.compileResult().testIncrementalFallbackReason());
        addMainCompileDiagnostics(attributes, result.compileResult().buildResult().mainCompileDiagnostics());
        addTestCompileDiagnostics(attributes, result.compileResult().testCompileDiagnostics());
        attributes.put(TimingAttributeKeys.TEST_RUNNER, result.testRunner());
        attributes.put(TimingAttributeKeys.TEST_RUNTIME_CLASSPATH_ENTRIES, Integer.toString(result.testRuntimeClasspathEntries()));
        attributes.put(TimingAttributeKeys.TEST_LAUNCHER_CLASSPATH_ENTRIES, Integer.toString(result.testLauncherClasspathEntries()));
        attributes.put(TimingAttributeKeys.TEST_DISCOVERY_SCAN_ROOTS, Integer.toString(result.testDiscoveryScanRoots()));
        addTestSelectionAttributes(attributes, result.testSelection());
        attributes.put(TimingAttributeKeys.TEST_JVM_ARGS, Integer.toString(result.testJvmArguments().values().size()));
        addMainFingerprintAttributes(attributes, result.compileResult().buildResult());
        addTestFingerprintAttributes(attributes, result.compileResult());
        addPlainJunitWorkerTimingAttributes(attributes, result);
        attributes.put(TimingAttributeKeys.OUTPUT_BYTES, Integer.toString(result.output().length()));
        return attributes;
    }

    static Map<String, String> testCompile(TestCompileResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MAIN_SOURCE_FILES, Integer.toString(result.buildResult().sourceCount()));
        attributes.put(TimingAttributeKeys.TEST_SOURCE_FILES, Integer.toString(result.sourceCount()));
        attributes.put(TimingAttributeKeys.TEST_RESOURCE_FILES, Integer.toString(result.resourceCount()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_SKIPPED, Boolean.toString(result.buildResult().mainCompilationSkipped()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_MODE, result.buildResult().mainCompilationMode());
        attributes.put(TimingAttributeKeys.MAIN_INCREMENTAL_FALLBACK_REASON, result.buildResult().mainIncrementalFallbackReason());
        attributes.put(TimingAttributeKeys.TEST_COMPILATION_SKIPPED, Boolean.toString(result.testCompilationSkipped()));
        attributes.put(TimingAttributeKeys.TEST_COMPILATION_MODE, result.testCompilationMode());
        attributes.put(TimingAttributeKeys.TEST_INCREMENTAL_FALLBACK_REASON, result.testIncrementalFallbackReason());
        addMainCompileDiagnostics(attributes, result.buildResult().mainCompileDiagnostics());
        addTestCompileDiagnostics(attributes, result.testCompileDiagnostics());
        addMainFingerprintAttributes(attributes, result.buildResult());
        addTestFingerprintAttributes(attributes, result);
        return attributes;
    }

    static Map<String, String> testExecution(TestRunResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.TEST_RUNNER, result.testRunner());
        attributes.put(TimingAttributeKeys.TEST_RUNTIME_CLASSPATH_ENTRIES, Integer.toString(result.testRuntimeClasspathEntries()));
        attributes.put(TimingAttributeKeys.TEST_LAUNCHER_CLASSPATH_ENTRIES, Integer.toString(result.testLauncherClasspathEntries()));
        attributes.put(TimingAttributeKeys.TEST_DISCOVERY_SCAN_ROOTS, Integer.toString(result.testDiscoveryScanRoots()));
        addTestSelectionAttributes(attributes, result.testSelection());
        attributes.put(TimingAttributeKeys.TEST_JVM_ARGS, Integer.toString(result.testJvmArguments().values().size()));
        addPlainJunitWorkerTimingAttributes(attributes, result);
        attributes.put(TimingAttributeKeys.OUTPUT_BYTES, Integer.toString(result.output().length()));
        return attributes;
    }

    static Map<String, String> workspaceTest(WorkspaceTestResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MEMBERS, Integer.toString(result.members().size()));
        attributes.put(TimingAttributeKeys.INCLUDED_MEMBERS, Integer.toString(result.includedMemberCount()));
        attributes.put(TimingAttributeKeys.SELECTED_MEMBERS, Integer.toString(result.selectedMemberCount()));
        attributes.put(TimingAttributeKeys.DEPENDENCY_MEMBERS, Integer.toString(result.dependencyMemberCount()));
        attributes.put(TimingAttributeKeys.MAIN_SOURCE_FILES, Integer.toString(result.mainSourceCount()));
        attributes.put(TimingAttributeKeys.TEST_SOURCE_FILES, Integer.toString(result.testSourceCount()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATIONS_SKIPPED, Integer.toString(result.mainCompilationSkippedCount()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATIONS_EXECUTED, Integer.toString(result.mainCompilationExecutedCount()));
        attributes.put(TimingAttributeKeys.TEST_COMPILATIONS_SKIPPED, Integer.toString(result.testCompilationSkippedCount()));
        attributes.put(TimingAttributeKeys.TEST_COMPILATIONS_EXECUTED, Integer.toString(result.testCompilationExecutedCount()));
        attributes.put(TimingAttributeKeys.TEST_RUNTIME_CLASSPATH_ENTRIES, Integer.toString(result.testRuntimeClasspathEntryCount()));
        attributes.put(TimingAttributeKeys.TEST_LAUNCHER_CLASSPATH_ENTRIES, Integer.toString(result.testLauncherClasspathEntryCount()));
        attributes.put(TimingAttributeKeys.TEST_DISCOVERY_SCAN_ROOTS, Integer.toString(result.testDiscoveryScanRootCount()));
        addMainFingerprintAttributes(
                attributes,
                result.mainFingerprintCheckNanos(),
                result.mainFingerprintWriteNanos());
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_CHECK_MILLIS, Long.toString(result.testFingerprintCheckMillis()));
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_CHECK_NANOS, Long.toString(result.testFingerprintCheckNanos()));
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_WRITE_MILLIS, Long.toString(result.testFingerprintWriteMillis()));
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_WRITE_NANOS, Long.toString(result.testFingerprintWriteNanos()));
        attributes.put(TimingAttributeKeys.TEST_CLASS_SELECTORS, Integer.toString(result.testClassSelectorCount()));
        attributes.put(TimingAttributeKeys.TEST_METHOD_SELECTORS, Integer.toString(result.testMethodSelectorCount()));
        attributes.put(TimingAttributeKeys.TEST_PATTERNS, Integer.toString(result.testPatternCount()));
        attributes.put(TimingAttributeKeys.TEST_INCLUDED_TAGS, Integer.toString(result.testIncludedTagCount()));
        attributes.put(TimingAttributeKeys.TEST_EXCLUDED_TAGS, Integer.toString(result.testExcludedTagCount()));
        attributes.put(TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.resolvedLockfile()));
        return attributes;
    }

    private static void addPlainJunitWorkerTimingAttributes(Map<String, String> attributes, TestRunResult result) {
        if (result.testRunnerStartupNanos() >= 0L) {
            attributes.put(TimingAttributeKeys.TEST_RUNNER_STARTUP_MILLIS, Long.toString(result.testRunnerStartupNanos() / 1_000_000L));
            attributes.put(TimingAttributeKeys.TEST_RUNNER_STARTUP_NANOS, Long.toString(result.testRunnerStartupNanos()));
        }
        if (result.testRunnerRequestNanos() >= 0L) {
            attributes.put(TimingAttributeKeys.TEST_RUNNER_REQUEST_MILLIS, Long.toString(result.testRunnerRequestNanos() / 1_000_000L));
            attributes.put(TimingAttributeKeys.TEST_RUNNER_REQUEST_NANOS, Long.toString(result.testRunnerRequestNanos()));
        }
    }

    private static void addMainCompileDiagnostics(Map<String, String> attributes, CompileDiagnostics diagnostics) {
        addCompileDiagnostics(attributes, TimingAttributeKeys.MAIN_PREFIX, diagnostics);
    }

    private static void addTestCompileDiagnostics(Map<String, String> attributes, CompileDiagnostics diagnostics) {
        addCompileDiagnostics(attributes, TimingAttributeKeys.TEST_PREFIX, diagnostics);
    }

    private static void addCompileDiagnostics(
            Map<String, String> attributes,
            String prefix,
            CompileDiagnostics diagnostics) {
        CompileDiagnostics values = diagnostics == null ? CompileDiagnostics.empty() : diagnostics;
        attributes.put(prefix + TimingAttributeKeys.SOURCES_ADDED_SUFFIX, Integer.toString(values.sourcesAdded()));
        attributes.put(prefix + TimingAttributeKeys.SOURCES_CHANGED_SUFFIX, Integer.toString(values.sourcesChanged()));
        attributes.put(prefix + TimingAttributeKeys.SOURCES_DELETED_SUFFIX, Integer.toString(values.sourcesDeleted()));
        attributes.put(prefix + TimingAttributeKeys.SOURCES_RECOMPILED_SUFFIX, Integer.toString(values.sourcesRecompiled()));
        attributes.put(
                prefix + TimingAttributeKeys.DEPENDENT_SOURCES_RECOMPILED_SUFFIX,
                Integer.toString(values.dependentSourcesRecompiled()));
        attributes.put(prefix + TimingAttributeKeys.CLASSES_DELETED_SUFFIX, Integer.toString(values.classesDeleted()));
        attributes.put(prefix + TimingAttributeKeys.ABI_CHANGED_CLASSES_SUFFIX, Integer.toString(values.abiChangedClasses()));
        attributes.put(
                prefix + TimingAttributeKeys.PACKAGE_PRIVATE_ABI_CHANGED_CLASSES_SUFFIX,
                Integer.toString(values.packagePrivateAbiChangedClasses()));
    }

    private static void addMainFingerprintAttributes(Map<String, String> attributes, BuildResult result) {
        addMainFingerprintAttributes(
                attributes,
                result.mainFingerprintCheckNanos(),
                result.mainFingerprintWriteNanos());
    }

    private static void addMainFingerprintAttributes(
            Map<String, String> attributes,
            long checkNanos,
            long writeNanos) {
        attributes.put(TimingAttributeKeys.MAIN_FINGERPRINT_CHECK_MILLIS, Long.toString(checkNanos / 1_000_000L));
        attributes.put(TimingAttributeKeys.MAIN_FINGERPRINT_CHECK_NANOS, Long.toString(checkNanos));
        attributes.put(TimingAttributeKeys.MAIN_FINGERPRINT_WRITE_MILLIS, Long.toString(writeNanos / 1_000_000L));
        attributes.put(TimingAttributeKeys.MAIN_FINGERPRINT_WRITE_NANOS, Long.toString(writeNanos));
    }

    private static void addTestFingerprintAttributes(Map<String, String> attributes, TestCompileResult result) {
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_CHECK_MILLIS, Long.toString(result.testFingerprintCheckMillis()));
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_CHECK_NANOS, Long.toString(result.testFingerprintCheckNanos()));
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_WRITE_MILLIS, Long.toString(result.testFingerprintWriteMillis()));
        attributes.put(TimingAttributeKeys.TEST_FINGERPRINT_WRITE_NANOS, Long.toString(result.testFingerprintWriteNanos()));
    }

    private static void addTestSelectionAttributes(Map<String, String> attributes, TestSelection selection) {
        attributes.put(TimingAttributeKeys.TEST_CLASS_SELECTORS, Integer.toString(selection.classSelectors().size()));
        attributes.put(TimingAttributeKeys.TEST_METHOD_SELECTORS, Integer.toString(selection.methodSelectors().size()));
        attributes.put(TimingAttributeKeys.TEST_PATTERNS, Integer.toString(selection.classNamePatterns().size()));
        attributes.put(TimingAttributeKeys.TEST_INCLUDED_TAGS, Integer.toString(selection.includedTags().size()));
        attributes.put(TimingAttributeKeys.TEST_EXCLUDED_TAGS, Integer.toString(selection.excludedTags().size()));
    }
}
