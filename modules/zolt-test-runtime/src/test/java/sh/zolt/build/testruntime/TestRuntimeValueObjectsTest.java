package sh.zolt.build.testruntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.build.BuildResult;
import sh.zolt.build.CompileDiagnostics;
import sh.zolt.build.coverage.CoverageResult;
import sh.zolt.build.coverage.CoverageReportSettings;
import sh.zolt.build.testruntime.compile.TestCompileResult;
import sh.zolt.test.TestSelection;
import sh.zolt.test.runtime.TestJvmArguments;
import sh.zolt.test.runtime.TestRunException;
import sh.zolt.test.shard.TestShardSpec;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestRuntimeValueObjectsTest {
    @TempDir
    private Path projectDir;

    @Test
    void testRunResultDefaultsNullableRuntimeDetails() {
        TestRunResult result = new TestRunResult(null, "ok\n", null, null, null, null, null);

        assertEquals("unknown", result.testRunner());
        assertEquals(0, result.testRuntimeClasspathEntries());
        assertEquals(0, result.testLauncherClasspathEntries());
        assertEquals(0, result.testDiscoveryScanRoots());
        assertEquals(-1L, result.testRunnerStartupNanos());
        assertEquals(-1L, result.testRunnerRequestNanos());
        assertTrue(result.testSelection().emptySelection());
        assertEquals(List.of(), result.testJvmArguments().values());
        assertTrue(result.reportsDirectory().isEmpty());
        assertTrue(result.profileDirectory().isEmpty());
    }

    @Test
    void testRunResultConstructorsPreserveOfficialMetricsShape() {
        TestRunResult legacy = new TestRunResult(null, "ok\n", 3, 2);
        TestRunResult withDiscovery = new TestRunResult(null, "ok\n", 4, 3, 2);
        TestRunResult withRunner = new TestRunResult(null, "ok\n", "zolt-junit-worker", 5, 4, 3);

        assertEquals("unknown", legacy.testRunner());
        assertEquals(3, legacy.testRuntimeClasspathEntries());
        assertEquals(2, legacy.testLauncherClasspathEntries());
        assertEquals(0, legacy.testDiscoveryScanRoots());
        assertEquals(2, withDiscovery.testDiscoveryScanRoots());
        assertEquals("zolt-junit-worker", withRunner.testRunner());
        assertEquals(5, withRunner.testRuntimeClasspathEntries());
        assertEquals(4, withRunner.testLauncherClasspathEntries());
        assertEquals(3, withRunner.testDiscoveryScanRoots());
    }

    @Test
    void testRunMetricsUsesUnknownRunnerForBlankValues() {
        TestRunMetrics metrics = new TestRunMetrics(" ", 1, 2, 3, 4L, 5L);

        assertEquals("unknown", metrics.testRunner());
        assertEquals(1, metrics.testRuntimeClasspathEntries());
        assertEquals(2, metrics.testLauncherClasspathEntries());
        assertEquals(3, metrics.testDiscoveryScanRoots());
        assertEquals(4L, metrics.testRunnerStartupNanos());
        assertEquals(5L, metrics.testRunnerRequestNanos());
    }

    @Test
    void reportSettingsDeriveWorkspaceAndShardEvidencePaths() {
        TestReportSettings reports = TestReportSettings.reportsDirectory(Path.of("target/test-reports"));

        assertEquals(
                Optional.of(Path.of("target/test-reports/modules/zolt-test-runtime")),
                reports.forWorkspaceMember("modules/zolt-test-runtime").reportsDirectory());
        assertTrue(TestReportSettings.disabled().forWorkspaceMember("modules/zolt-test-runtime").reportsDirectory().isEmpty());
        TestReportSettings shardReports = reports.forShard("fast suite!", new TestShardSpec(2, 3));
        assertEquals(
                Optional.of(Path.of("target/test-reports/shards/fast_suite_/shard-2-of-3")),
                shardReports.reportsDirectory());
        assertEquals(
                Optional.of(projectDir.resolve("target/test-reports/shards/fast_suite_/shard-2-of-3")
                        .toAbsolutePath()
                        .normalize()),
                shardReports.absoluteReportsDirectory(projectDir));
    }

    @Test
    void reportSettingsRejectOutputOutsideProjectWithRemediation() {
        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> TestReportSettings.reportsDirectory(Path.of("../test-reports"))
                        .absoluteReportsDirectory(projectDir));

        assertTrue(exception.getMessage().contains("--reports-dir"));
        assertTrue(exception.getMessage().contains("Use a project-relative path"));
    }

    @Test
    void coverageResultDefaultsOptionalReports() {
        CoverageResult result = new CoverageResult(
                new TestRunResult(null, "ok\n"),
                "report\n",
                Path.of("target/coverage/jacoco.exec"),
                null,
                null);

        assertTrue(result.xmlReport().isEmpty());
        assertTrue(result.htmlDirectory().isEmpty());
    }

    @Test
    void coverageSettingsDefaultsNullablePathsAndReports() {
        CoverageReportSettings settings = new CoverageReportSettings(true, true, null, null, null, null);

        assertEquals(Path.of("target/coverage/jacoco.exec"), settings.execFile());
        assertEquals(Path.of("target/coverage/jacoco.xml"), settings.xmlReport());
        assertEquals(Path.of("target/coverage/html"), settings.htmlDirectory());
        assertEquals(Optional.of(Path.of("target/coverage/test-reports")), settings.testReports().reportsDirectory());
    }

    @Test
    void testCompileResultNormalizesModesDiagnosticsAndTimings() {
        BuildResult buildResult = new BuildResult(Optional.empty(), 0, 0, Path.of("target/classes"), "");
        TestCompileResult result = new TestCompileResult(
                buildResult,
                2,
                1,
                Path.of("target/test-classes"),
                "",
                false,
                null,
                null,
                null,
                -2L,
                2_500_000L);
        TestCompileResult skipped = new TestCompileResult(
                buildResult,
                0,
                0,
                Path.of("target/test-classes"),
                "",
                true,
                "incremental",
                "stale fingerprint",
                new CompileDiagnostics(1, 2, 3, 4, 5, 6, 7, 8),
                4_000_000L,
                -4L);

        assertEquals("full", result.testCompilationMode());
        assertEquals("", result.testIncrementalFallbackReason());
        assertEquals(CompileDiagnostics.empty(), result.testCompileDiagnostics());
        assertEquals(0L, result.testFingerprintCheckNanos());
        assertEquals(2L, result.testFingerprintWriteMillis());
        assertEquals("skipped", skipped.testCompilationMode());
        assertEquals("stale fingerprint", skipped.testIncrementalFallbackReason());
        assertEquals(4L, skipped.testFingerprintCheckMillis());
        assertEquals(0L, skipped.testFingerprintWriteNanos());
    }

    @Test
    void testRunResultKeepsExplicitSelectionArgumentsAndReports() {
        TestSelection selection = TestSelection.fromCli(
                List.of("com.example.MainTest"),
                List.of(),
                List.of("fast"),
                List.of("slow"));
        TestJvmArguments jvmArguments = new TestJvmArguments(List.of("-Dmode=test"));

        TestRunResult result = new TestRunResult(
                null,
                "ok\n",
                TestRunResult.metrics("junit-console", 3, 2, 1, 10L, 20L),
                selection,
                jvmArguments,
                Optional.of(Path.of("target/test-reports")),
                Optional.of(Path.of("target/test-profile")));

        assertEquals(selection, result.testSelection());
        assertEquals(jvmArguments, result.testJvmArguments());
        assertEquals(Optional.of(Path.of("target/test-reports")), result.reportsDirectory());
        assertEquals(Optional.of(Path.of("target/test-profile")), result.profileDirectory());
    }
}
