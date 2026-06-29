package com.zolt.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.BuildResult;
import com.zolt.build.CoverageReportSettings;
import com.zolt.build.CoverageTooling;
import com.zolt.build.JavaRunResult;
import com.zolt.build.testruntime.TestCompileResult;
import com.zolt.build.testruntime.TestJvmArguments;
import com.zolt.build.testruntime.TestReportSettings;
import com.zolt.build.testruntime.TestRunResult;
import com.zolt.classpath.Classpath;
import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import com.zolt.resolve.ResolveResult;
import com.zolt.test.TestSelection;
import com.zolt.test.TestShardSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceCoverageServiceTest {
    @TempDir
    private Path tempDir;

    @Test
    void runsSelectedWorkspaceMembersAndWritesAggregateReport() throws IOException {
        Path workspaceRoot = tempDir.resolve("workspace");
        Path apiDir = workspaceRoot.resolve("apps/api");
        Path coreDir = workspaceRoot.resolve("modules/core");
        Files.createDirectories(apiDir.resolve("src/main/java"));
        Files.createDirectories(coreDir.resolve("src/main/java"));
        ProjectConfig apiConfig = config("api");
        ProjectConfig coreConfig = config("core");
        Workspace workspace = new Workspace(
                workspaceRoot,
                workspaceRoot.resolve("zolt-workspace.toml"),
                new WorkspaceConfig(
                        "workspace",
                        List.of("modules/core", "apps/api"),
                        List.of("apps/api"),
                        Map.of(),
                        Map.of()),
                List.of(
                        new WorkspaceMember("modules/core", coreDir, coreConfig),
                        new WorkspaceMember("apps/api", apiDir, apiConfig)),
                List.of(),
                List.of("modules/core", "apps/api"));
        WorkspaceBuildPlan plan = new WorkspaceBuildPlan(
                workspace,
                new WorkspaceSelection(List.of("modules/core", "apps/api"), List.of("apps/api")),
                Optional.empty(),
                new ZoltLockfile(1, List.of(), List.of()));
        WorkspaceBuildResult buildResult = new WorkspaceBuildResult(
                Optional.empty(),
                List.of(
                        memberBuild("modules/core", coreDir),
                        memberBuild("apps/api", apiDir)));
        List<TestJvmArguments> testJvmArguments = new ArrayList<>();
        List<Path> reportClassfileRoots = new ArrayList<>();
        List<Path> reportSourceRoots = new ArrayList<>();
        Path agentJar = tempDir.resolve("org.jacoco.agent-0.8.14-runtime.jar");
        Path cliJar = tempDir.resolve("org.jacoco.cli-0.8.14.jar");
        WorkspaceCoverageService service = new WorkspaceCoverageService(
                (startDirectory, cacheRoot) -> new ResolveResult(2, 0, 0, workspaceRoot.resolve("zolt.lock")),
                new WorkspaceCoverageService.CoverageWorkspaceTests() {
                    @Override
                    public WorkspaceBuildPlan planTests(
                            Path startDirectory,
                            Path cacheRoot,
                            WorkspaceSelectionRequest selectionRequest) {
                        assertEquals(apiDir, startDirectory);
                        assertTrue(selectionRequest.all());
                        return plan;
                    }

                    @Override
                    public WorkspaceBuildResult buildTestInputs(WorkspaceBuildPlan requestedPlan, Path cacheRoot) {
                        assertEquals(plan, requestedPlan);
                        return buildResult;
                    }

                    @Override
                    public WorkspaceTestResult runTests(
                            WorkspaceBuildPlan requestedPlan,
                            WorkspaceBuildResult requestedBuildResult,
                            Path cacheRoot,
                            TestSelection testSelection,
                            TestJvmArguments jvmArguments,
                            TestReportSettings reportSettings,
                            List<String> cliEvents,
                            String suiteName,
                            TestShardSpec shard) {
                        assertEquals(plan, requestedPlan);
                        assertEquals(buildResult, requestedBuildResult);
                        assertEquals("all", suiteName);
                        assertEquals(null, shard);
                        assertEquals(Optional.of(Path.of("target/coverage/test-reports")), reportSettings.reportsDirectory());
                        testJvmArguments.add(jvmArguments);
                        return new WorkspaceTestResult(
                                Optional.empty(),
                                requestedBuildResult.members(),
                                List.of(new WorkspaceTestResult.MemberTestRunResult(
                                        "apps/api",
                                        new TestRunResult(
                                                testCompile(apiDir),
                                                "api tests\n",
                                                TestRunResult.metrics("junit-console", 1, 1, 1, -1L, -1L),
                                                testSelection,
                                                jvmArguments,
                                                Optional.of(apiDir.resolve("target/coverage/test-reports/apps/api"))))));
                    }
                },
                new WorkspaceCoverageService.CoverageReporter() {
                    @Override
                    public CoverageTooling lockedCoverageTooling(Path lockfileDirectory, Path cacheRoot) {
                        assertEquals(workspaceRoot, lockfileDirectory);
                        return new CoverageTooling(agentJar, List.of(cliJar));
                    }

                    @Override
                    public TestJvmArguments coverageJvmArguments(Path requestedAgentJar, Path execFile, boolean append) {
                        assertEquals(agentJar, requestedAgentJar);
                        assertEquals(workspaceRoot.resolve("target/coverage/jacoco.exec"), execFile);
                        assertTrue(append);
                        return new TestJvmArguments(List.of("-javaagent:" + requestedAgentJar + "=destfile=" + execFile + ",append=true"));
                    }

                    @Override
                    public JavaRunResult runReport(
                            Path projectRoot,
                            ProjectConfig config,
                            CoverageReportSettings settings,
                            Path execFile,
                            List<Path> cliClasspath,
                            List<Path> classfileRoots,
                            List<Path> sourceRoots) {
                        assertEquals(workspaceRoot, projectRoot);
                        assertEquals(coreConfig, config);
                        assertEquals(workspaceRoot.resolve("target/coverage/jacoco.exec"), execFile);
                        assertEquals(List.of(cliJar), cliClasspath);
                        reportClassfileRoots.addAll(classfileRoots);
                        reportSourceRoots.addAll(sourceRoots);
                        return new JavaRunResult("org.jacoco.cli.internal.Main", "aggregate report\n");
                    }
                });

        WorkspaceCoverageResult result = service.runCoverage(
                apiDir,
                tempDir.resolve("cache"),
                new WorkspaceSelectionRequest(true, List.of()),
                TestSelection.empty(),
                CoverageReportSettings.defaults(),
                List.of("failed"));

        assertEquals(workspaceRoot.resolve("target/coverage/jacoco.exec"), result.execFile());
        assertEquals(Optional.of(workspaceRoot.resolve("target/coverage/jacoco.xml")), result.xmlReport());
        assertEquals(Optional.of(workspaceRoot.resolve("target/coverage/html")), result.htmlDirectory());
        assertEquals(1, result.members().size());
        assertEquals("apps/api", result.members().getFirst().member());
        assertEquals("aggregate report\n", result.reportOutput());
        assertEquals(1, testJvmArguments.size());
        assertTrue(testJvmArguments.getFirst().values().getFirst().contains("append=true"));
        assertEquals(List.of(
                coreDir.resolve("target/classes").toAbsolutePath().normalize(),
                apiDir.resolve("target/classes").toAbsolutePath().normalize()), reportClassfileRoots);
        assertEquals(List.of(
                coreDir.resolve("src/main/java").toAbsolutePath().normalize(),
                apiDir.resolve("src/main/java").toAbsolutePath().normalize()), reportSourceRoots);
    }

    @Test
    void shardCoverageUsesShardSpecificAggregateOutputsAndMemberReports() throws IOException {
        Path workspaceRoot = tempDir.resolve("workspace");
        Path apiDir = workspaceRoot.resolve("apps/api");
        Files.createDirectories(apiDir.resolve("src/main/java"));
        ProjectConfig apiConfig = config("api");
        Workspace workspace = new Workspace(
                workspaceRoot,
                workspaceRoot.resolve("zolt-workspace.toml"),
                new WorkspaceConfig(
                        "workspace",
                        List.of("apps/api"),
                        List.of("apps/api"),
                        Map.of(),
                        Map.of()),
                List.of(new WorkspaceMember("apps/api", apiDir, apiConfig)),
                List.of(),
                List.of("apps/api"));
        WorkspaceBuildPlan plan = new WorkspaceBuildPlan(
                workspace,
                new WorkspaceSelection(List.of("apps/api"), List.of("apps/api")),
                Optional.empty(),
                new ZoltLockfile(1, List.of(), List.of()));
        WorkspaceBuildResult buildResult = new WorkspaceBuildResult(
                Optional.empty(),
                List.of(memberBuild("apps/api", apiDir)));
        List<TestReportSettings> reportSettings = new ArrayList<>();
        List<TestShardSpec> shards = new ArrayList<>();
        Path agentJar = tempDir.resolve("org.jacoco.agent-0.8.14-runtime.jar");
        Path cliJar = tempDir.resolve("org.jacoco.cli-0.8.14.jar");
        WorkspaceCoverageService service = new WorkspaceCoverageService(
                (startDirectory, cacheRoot) -> new ResolveResult(1, 0, 0, workspaceRoot.resolve("zolt.lock")),
                new WorkspaceCoverageService.CoverageWorkspaceTests() {
                    @Override
                    public WorkspaceBuildPlan planTests(
                            Path startDirectory,
                            Path cacheRoot,
                            WorkspaceSelectionRequest selectionRequest) {
                        return plan;
                    }

                    @Override
                    public WorkspaceBuildResult buildTestInputs(WorkspaceBuildPlan requestedPlan, Path cacheRoot) {
                        return buildResult;
                    }

                    @Override
                    public WorkspaceTestResult runTests(
                            WorkspaceBuildPlan requestedPlan,
                            WorkspaceBuildResult requestedBuildResult,
                            Path cacheRoot,
                            TestSelection testSelection,
                            TestJvmArguments jvmArguments,
                            TestReportSettings requestedReportSettings,
                            List<String> cliEvents,
                            String suiteName,
                            TestShardSpec shard) {
                        assertEquals("fast", suiteName);
                        reportSettings.add(requestedReportSettings);
                        shards.add(shard);
                        return new WorkspaceTestResult(
                                Optional.empty(),
                                requestedBuildResult.members(),
                                List.of(new WorkspaceTestResult.MemberTestRunResult(
                                        "apps/api",
                                        new TestRunResult(
                                                testCompile(apiDir),
                                                "api tests\n",
                                                TestRunResult.metrics("junit-console", 1, 1, 1, -1L, -1L),
                                                testSelection,
                                                jvmArguments,
                                                requestedReportSettings.reportsDirectory()))));
                    }
                },
                new WorkspaceCoverageService.CoverageReporter() {
                    @Override
                    public CoverageTooling lockedCoverageTooling(Path lockfileDirectory, Path cacheRoot) {
                        return new CoverageTooling(agentJar, List.of(cliJar));
                    }

                    @Override
                    public TestJvmArguments coverageJvmArguments(Path requestedAgentJar, Path execFile, boolean append) {
                        assertEquals(workspaceRoot.resolve("target/coverage/shards/fast/shard-2-of-4/jacoco.exec"), execFile);
                        return new TestJvmArguments(List.of("-javaagent:" + requestedAgentJar + "=destfile=" + execFile));
                    }

                    @Override
                    public JavaRunResult runReport(
                            Path projectRoot,
                            ProjectConfig config,
                            CoverageReportSettings settings,
                            Path execFile,
                            List<Path> cliClasspath,
                            List<Path> classfileRoots,
                            List<Path> sourceRoots) {
                        assertEquals(workspaceRoot.resolve("target/coverage/shards/fast/shard-2-of-4/jacoco.exec"), execFile);
                        return new JavaRunResult("org.jacoco.cli.internal.Main", "aggregate report\n");
                    }
                });

        WorkspaceCoverageResult result = service.runCoverage(
                apiDir,
                tempDir.resolve("cache"),
                WorkspaceSelectionRequest.defaults(),
                TestSelection.empty(),
                CoverageReportSettings.defaults(),
                List.of(),
                "fast",
                new TestShardSpec(2, 4));

        Path shardRoot = workspaceRoot.resolve("target/coverage/shards/fast/shard-2-of-4");
        assertEquals(shardRoot.resolve("jacoco.exec"), result.execFile());
        assertEquals(Optional.of(shardRoot.resolve("jacoco.xml")), result.xmlReport());
        assertEquals(Optional.of(shardRoot.resolve("html")), result.htmlDirectory());
        assertEquals(List.of(new TestShardSpec(2, 4)), shards);
        assertEquals(Optional.of(Path.of("target/coverage/test-reports")), reportSettings.getFirst().reportsDirectory());
        assertEquals(Optional.of(Path.of("target/coverage/test-reports")), result.members().getFirst().result().reportsDirectory());
    }

    private static WorkspaceBuildResult.MemberBuildResult memberBuild(String member, Path memberDir) {
        return new WorkspaceBuildResult.MemberBuildResult(
                member,
                build(memberDir),
                emptyClasspaths());
    }

    private static TestCompileResult testCompile(Path memberDir) {
        return new TestCompileResult(
                build(memberDir),
                1,
                0,
                memberDir.resolve("target/test-classes"),
                "");
    }

    private static BuildResult build(Path memberDir) {
        return new BuildResult(
                Optional.empty(),
                1,
                0,
                memberDir.resolve("target/classes"),
                "");
    }

    private static ClasspathSet emptyClasspaths() {
        Classpath empty = new Classpath(List.of());
        return new ClasspathSet(empty, empty, empty, empty, empty, empty);
    }

    private static ProjectConfig config(String name) {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata(name, "0.1.0", "com.example", "21", Optional.empty()),
                Map.of(),
                Map.of(),
                Map.of("org.junit.jupiter:junit-jupiter", "5.11.4"),
                BuildSettings.defaults());
    }
}
