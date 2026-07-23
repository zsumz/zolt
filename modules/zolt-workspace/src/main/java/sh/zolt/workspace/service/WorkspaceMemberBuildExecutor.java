package sh.zolt.workspace.service;

import sh.zolt.build.BuildException;
import sh.zolt.build.BuildService;
import sh.zolt.build.JavacException;
import sh.zolt.build.cache.BuildCacheService;
import sh.zolt.classpath.ClasspathSet;
import sh.zolt.classpath.ResolvedClasspathPackage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class WorkspaceMemberBuildExecutor {
    private final BuildService buildService;
    private final WorkspaceJdkCheckerResolver jdkCheckers;
    private final WorkspaceBuildBatchPlanner batchPlanner;
    private final BuildCacheService buildCacheService;

    WorkspaceMemberBuildExecutor(
            BuildService buildService,
            WorkspaceJdkCheckerResolver jdkCheckers,
            WorkspaceBuildBatchPlanner batchPlanner) {
        this(buildService, jdkCheckers, batchPlanner, BuildCacheService.disabled());
    }

    private WorkspaceMemberBuildExecutor(
            BuildService buildService,
            WorkspaceJdkCheckerResolver jdkCheckers,
            WorkspaceBuildBatchPlanner batchPlanner,
            BuildCacheService buildCacheService) {
        this.buildService = buildService;
        this.jdkCheckers = jdkCheckers;
        this.batchPlanner = batchPlanner;
        this.buildCacheService = buildCacheService;
    }

    WorkspaceMemberBuildExecutor withJdkCheckers(WorkspaceJdkCheckerResolver jdkCheckers) {
        return new WorkspaceMemberBuildExecutor(buildService, jdkCheckers, batchPlanner, buildCacheService);
    }

    WorkspaceMemberBuildExecutor withBuildCache(BuildCacheService buildCacheService) {
        return new WorkspaceMemberBuildExecutor(buildService, jdkCheckers, batchPlanner, buildCacheService);
    }

    Result build(
            Workspace workspace,
            WorkspaceSelection selection,
            Map<String, WorkspaceMember> membersByPath,
            Map<String, ClasspathSet> classpathsByMember,
            Map<String, List<ResolvedClasspathPackage>> classpathPackagesByMember) {
        List<List<String>> batches = batchPlanner.batches(workspace, selection.includedMembers());
        if (batches.isEmpty()) {
            return new Result(List.of(), 0, 0);
        }
        int concurrency = workspaceBuildConcurrency(selection.includedMembers().size());
        Map<String, WorkspaceBuildResult.MemberBuildResult> resultsByMember = new LinkedHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            for (List<String> batch : batches) {
                Map<String, Future<WorkspaceBuildResult.MemberBuildResult>> futures = new LinkedHashMap<>();
                for (String memberPath : batch) {
                    futures.put(memberPath, executor.submit(buildMemberTask(
                            workspace,
                            memberPath,
                            membersByPath,
                            classpathsByMember,
                            classpathPackagesByMember)));
                }
                for (Map.Entry<String, Future<WorkspaceBuildResult.MemberBuildResult>> entry : futures.entrySet()) {
                    resultsByMember.put(entry.getKey(), getMemberBuildResult(entry.getValue()));
                }
            }
        } finally {
            executor.shutdownNow();
        }
        List<WorkspaceBuildResult.MemberBuildResult> orderedResults = new ArrayList<>();
        for (String memberPath : selection.includedMembers()) {
            orderedResults.add(resultsByMember.get(memberPath));
        }
        return new Result(List.copyOf(orderedResults), batches.size(), concurrency);
    }

    private Callable<WorkspaceBuildResult.MemberBuildResult> buildMemberTask(
            Workspace workspace,
            String memberPath,
            Map<String, WorkspaceMember> membersByPath,
            Map<String, ClasspathSet> classpathsByMember,
            Map<String, List<ResolvedClasspathPackage>> classpathPackagesByMember) {
        return () -> {
            WorkspaceMember member = membersByPath.get(memberPath);
            ClasspathSet classpaths = classpathsByMember.get(member.path());
            try {
                return new WorkspaceBuildResult.MemberBuildResult(
                        member.path(),
                        buildService
                                .withJdkChecker(jdkCheckers.forMember(workspace, member))
                                .withBuildCache(buildCacheService)
                                .build(
                                        member.directory(),
                                        member.config(),
                                        classpaths),
                        classpaths,
                        classpathPackagesByMember.get(member.path()));
            } catch (JavacException exception) {
                throw new JavacException(
                        exception.getMessage()
                                + "\nWorkspace member `"
                                + member.path()
                                + "` failed to compile. If the missing type comes from a dependency of another workspace member, declare it directly in this member or move it to [api.dependencies] in the member that exposes it.",
                        exception);
            }
        };
    }

    private static WorkspaceBuildResult.MemberBuildResult getMemberBuildResult(
            Future<WorkspaceBuildResult.MemberBuildResult> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BuildException("Workspace build was interrupted while waiting for member compilation.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new BuildException("Workspace build failed while compiling a member.", cause);
        }
    }

    private static int workspaceBuildConcurrency(int memberCount) {
        if (memberCount <= 1) {
            return 1;
        }
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(memberCount, processors));
    }

    record Result(
            List<WorkspaceBuildResult.MemberBuildResult> results,
            int waveCount,
            int maxWorkers) {
        Result {
            results = List.copyOf(results);
        }
    }
}
