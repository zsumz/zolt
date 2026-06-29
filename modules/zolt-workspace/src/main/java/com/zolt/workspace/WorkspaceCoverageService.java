package com.zolt.workspace;

import com.zolt.build.CoverageException;
import com.zolt.build.CoverageReportSettings;
import com.zolt.build.CoverageService;
import com.zolt.build.CoverageTooling;
import com.zolt.build.JavaRunResult;
import com.zolt.build.testruntime.TestJvmArguments;
import com.zolt.resolve.ResolveResult;
import com.zolt.test.TestSelection;
import com.zolt.test.TestShardSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WorkspaceCoverageService {
    private final CoverageWorkspaceResolver workspaceResolver;
    private final CoverageWorkspaceTests workspaceTests;
    private final CoverageReporter coverageReporter;

    public WorkspaceCoverageService() {
        this(defaultResolver(), defaultWorkspaceTests(), defaultCoverageReporter());
    }

    WorkspaceCoverageService(
            CoverageWorkspaceResolver workspaceResolver,
            CoverageWorkspaceTests workspaceTests,
            CoverageReporter coverageReporter) {
        this.workspaceResolver = workspaceResolver;
        this.workspaceTests = workspaceTests;
        this.coverageReporter = coverageReporter;
    }

    public WorkspaceCoverageResult runCoverage(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest,
            TestSelection testSelection,
            CoverageReportSettings reportSettings,
            List<String> cliEvents) {
        return runCoverage(startDirectory, cacheRoot, selectionRequest, testSelection, reportSettings, cliEvents, "all", null);
    }

    public WorkspaceCoverageResult runCoverage(
            Path startDirectory,
            Path cacheRoot,
            WorkspaceSelectionRequest selectionRequest,
            TestSelection testSelection,
            CoverageReportSettings reportSettings,
            List<String> cliEvents,
            String suiteName,
            TestShardSpec shard) {
        CoverageReportSettings settings = reportSettings == null ? CoverageReportSettings.defaults() : reportSettings;
        settings = settings.forShard(suiteName, shard);
        ResolveResult resolveResult = workspaceResolver.resolveWithCoverageTooling(startDirectory, cacheRoot);
        WorkspaceBuildPlan plan = workspaceTests.planTests(startDirectory, cacheRoot, selectionRequest);
        WorkspaceBuildResult buildResult = workspaceTests.buildTestInputs(plan, cacheRoot);
        Workspace workspace = plan.workspace();
        Path workspaceRoot = workspace.root().toAbsolutePath().normalize();
        CoverageTooling tooling = coverageReporter.lockedCoverageTooling(workspaceRoot, cacheRoot);
        Path execFile = settings.absoluteExecFile(workspaceRoot);
        recreateExecFile(execFile);
        TestJvmArguments coverageJvmArguments = coverageReporter.coverageJvmArguments(tooling.agentJar(), execFile, true);
        WorkspaceTestResult testResult = workspaceTests.runTests(
                plan,
                buildResult,
                cacheRoot,
                testSelection,
                coverageJvmArguments,
                settings.testReports(),
                cliEvents,
                suiteName,
                shard);
        List<WorkspaceMember> reportMembers = reportMembers(workspace, buildResult);
        List<Path> classfileRoots = reportMembers.stream()
                .map(member -> member.directory().resolve(member.config().build().output()).toAbsolutePath().normalize())
                .toList();
        List<Path> sourceRoots = reportMembers.stream()
                .map(member -> member.directory().resolve(member.config().build().source()).toAbsolutePath().normalize())
                .toList();
        JavaRunResult reportResult = coverageReporter.runReport(
                workspaceRoot,
                reportMembers.getFirst().config(),
                settings,
                execFile,
                tooling.cliClasspath(),
                classfileRoots,
                sourceRoots);
        return new WorkspaceCoverageResult(
                Optional.of(resolveResult),
                buildResult.members(),
                testResult.members().stream()
                        .map(member -> new WorkspaceCoverageResult.MemberCoverageRunResult(member.member(), member.result()))
                        .toList(),
                reportResult.output(),
                execFile,
                settings.absoluteXmlReport(workspaceRoot),
                settings.absoluteHtmlDirectory(workspaceRoot),
                classfileRoots.size(),
                sourceRoots.size());
    }

    private static List<WorkspaceMember> reportMembers(Workspace workspace, WorkspaceBuildResult buildResult) {
        Map<String, WorkspaceMember> membersByPath = new LinkedHashMap<>();
        for (WorkspaceMember member : workspace.members()) {
            membersByPath.put(member.path(), member);
        }
        List<WorkspaceMember> members = new ArrayList<>();
        for (WorkspaceBuildResult.MemberBuildResult memberBuild : buildResult.members()) {
            WorkspaceMember member = membersByPath.get(memberBuild.member());
            if (member != null) {
                members.add(member);
            }
        }
        if (members.isEmpty()) {
            throw new CoverageException("Workspace coverage requires at least one selected workspace member.");
        }
        return members;
    }

    private static void recreateExecFile(Path execFile) {
        try {
            Path parent = execFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.deleteIfExists(execFile);
        } catch (IOException exception) {
            throw new CoverageException(
                    "Could not prepare workspace coverage execution data at "
                            + execFile
                            + ". Check coverage output permissions, then run `zolt coverage --workspace` again.",
                    exception);
        }
    }

    private static CoverageWorkspaceResolver defaultResolver() {
        WorkspaceResolveService service = new WorkspaceResolveService();
        return service::resolveWithCoverageTooling;
    }

    private static CoverageWorkspaceTests defaultWorkspaceTests() {
        WorkspaceTestService service = new WorkspaceTestService();
        return new CoverageWorkspaceTests() {
            @Override
            public WorkspaceBuildPlan planTests(
                    Path startDirectory,
                    Path cacheRoot,
                    WorkspaceSelectionRequest selectionRequest) {
                return service.planTests(startDirectory, cacheRoot, selectionRequest);
            }

            @Override
            public WorkspaceBuildResult buildTestInputs(WorkspaceBuildPlan plan, Path cacheRoot) {
                return service.buildTestInputs(plan, cacheRoot);
            }

            @Override
            public WorkspaceTestResult runTests(
                    WorkspaceBuildPlan plan,
                    WorkspaceBuildResult buildResult,
                    Path cacheRoot,
                    TestSelection testSelection,
                    TestJvmArguments jvmArguments,
                    com.zolt.build.testruntime.TestReportSettings reportSettings,
                    List<String> cliEvents,
                    String suiteName,
                    TestShardSpec shard) {
                return service.runTests(
                        plan,
                        buildResult,
                        cacheRoot,
                        testSelection,
                        jvmArguments,
                        reportSettings,
                        cliEvents,
                        suiteName,
                        shard);
            }
        };
    }

    private static CoverageReporter defaultCoverageReporter() {
        CoverageService service = new CoverageService();
        return new CoverageReporter() {
            @Override
            public CoverageTooling lockedCoverageTooling(Path lockfileDirectory, Path cacheRoot) {
                return service.lockedCoverageTooling(lockfileDirectory, cacheRoot);
            }

            @Override
            public TestJvmArguments coverageJvmArguments(Path agentJar, Path execFile, boolean append) {
                return service.coverageJvmArguments(agentJar, execFile, append);
            }

            @Override
            public JavaRunResult runReport(
                    Path projectRoot,
                    com.zolt.project.ProjectConfig config,
                    CoverageReportSettings settings,
                    Path execFile,
                    List<Path> cliClasspath,
                    List<Path> classfileRoots,
                    List<Path> sourceRoots) {
                return service.runReport(
                        projectRoot,
                        config,
                        settings,
                        execFile,
                        cliClasspath,
                        classfileRoots,
                        sourceRoots);
            }
        };
    }

    @FunctionalInterface
    interface CoverageWorkspaceResolver {
        ResolveResult resolveWithCoverageTooling(Path startDirectory, Path cacheRoot);
    }

    interface CoverageWorkspaceTests {
        WorkspaceBuildPlan planTests(
                Path startDirectory,
                Path cacheRoot,
                WorkspaceSelectionRequest selectionRequest);

        WorkspaceBuildResult buildTestInputs(WorkspaceBuildPlan plan, Path cacheRoot);

        WorkspaceTestResult runTests(
                WorkspaceBuildPlan plan,
                WorkspaceBuildResult buildResult,
                Path cacheRoot,
                TestSelection testSelection,
                TestJvmArguments jvmArguments,
                com.zolt.build.testruntime.TestReportSettings reportSettings,
                List<String> cliEvents,
                String suiteName,
                TestShardSpec shard);
    }

    interface CoverageReporter {
        CoverageTooling lockedCoverageTooling(Path lockfileDirectory, Path cacheRoot);

        TestJvmArguments coverageJvmArguments(Path agentJar, Path execFile, boolean append);

        JavaRunResult runReport(
                Path projectRoot,
                com.zolt.project.ProjectConfig config,
                CoverageReportSettings settings,
                Path execFile,
                List<Path> cliClasspath,
                List<Path> classfileRoots,
                List<Path> sourceRoots);
    }
}
