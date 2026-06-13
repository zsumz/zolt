package com.zolt.cli.command;

import com.zolt.build.BuildException;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavaRunException;
import com.zolt.build.JavacException;
import com.zolt.build.ManifestGenerationException;
import com.zolt.build.PackageException;
import com.zolt.build.ResourceCopyException;
import com.zolt.build.RunPackageException;
import com.zolt.build.RunPackageResult;
import com.zolt.build.RunPackageService;
import com.zolt.build.SourceDiscoveryException;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.ZoltCli;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.WorkspaceBuildPlan;
import com.zolt.workspace.WorkspaceBuildResult;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspacePackageResult;
import com.zolt.workspace.WorkspaceRunPackageResult;
import com.zolt.workspace.WorkspaceRunPackageService;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "run-package", description = "Run a packaged thin jar with runtime dependencies.")
public final class RunPackageCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final RunPackageService runPackageService;
    private final WorkspaceRunPackageService workspaceRunPackageService;
    private final CommandLockfiles lockfiles;

    @Parameters(arity = "0..*", paramLabel = "ARGS", description = "Arguments passed to the application after `--`.")
    private List<String> arguments = List.of();

    @Option(names = "--workspace", description = "Run packaged workspace members in dependency order.")
    private boolean workspace;

    @Option(names = "--all", description = "Select every workspace member.")
    private boolean all;

    @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
    private List<String> members = List.of();

    @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
    private List<String> memberGroups = List.of();

    @Option(names = "--mode", description = "Package mode: thin, spring-boot, war, spring-boot-war, quarkus, or uber.")
    private String mode;

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Mixin
    private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

    @Spec
    private CommandSpec spec;

    public RunPackageCommand() {
        this(new ZoltTomlParser(), new RunPackageService(), new WorkspaceRunPackageService(), new CommandLockfiles());
    }

    RunPackageCommand(
            ZoltTomlParser tomlParser,
            RunPackageService runPackageService,
            WorkspaceRunPackageService workspaceRunPackageService,
            CommandLockfiles lockfiles) {
        this.tomlParser = tomlParser;
        this.runPackageService = runPackageService;
        this.workspaceRunPackageService = workspaceRunPackageService;
        this.lockfiles = lockfiles;
    }

    @Override
    public void run() {
        TimingRecorder timings = CommandTimings.recorder(timingOptions);
        try {
            Optional<PackageMode> packageModeOverride = CommandPackageSupport.packageModeOverride(mode);
            if (workspace) {
                runWorkspacePackages(timings, packageModeOverride);
                return;
            }
            runSinglePackage(timings, packageModeOverride);
        } catch (BuildException
                | JavacException
                | GroovyCompileException
                | JavaRunException
                | ManifestGenerationException
                | PackageException
                | ResourceCopyException
                | RunPackageException
                | SourceDiscoveryException
                | LockfileReadException
                | ResolveException
                | WorkspaceConfigException
                | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        } finally {
            CommandTimings.print(spec, "run-package", workingDirectory, timingOptions, timings);
        }
    }

    private void runWorkspacePackages(
            TimingRecorder timings,
            Optional<PackageMode> packageModeOverride) {
        lockfiles.requireFreshWorkspaceLockfile(workingDirectory, cacheRoot, false);
        WorkspaceRunPackageResult result = timings.measure(
                "run workspace packages",
                () -> {
                    WorkspaceBuildPlan plan = timings.measure(
                            "plan workspace run packages",
                            () -> workspaceRunPackageService.planRunPackages(
                                    workingDirectory,
                                    cacheRoot,
                                    CommandWorkspaceSelections.from(all, members, memberGroups)),
                            CommandBuildAttributes::workspaceBuildPlan);
                    WorkspaceBuildResult buildResult = timings.measure(
                            "build workspace run-package inputs",
                            () -> workspaceRunPackageService.buildRunPackageInputs(plan, cacheRoot),
                            CommandBuildAttributes::workspaceBuild);
                    WorkspacePackageResult packageResult = timings.measure(
                            "assemble workspace run packages",
                            () -> workspaceRunPackageService.packageRunPackageInputs(
                                    plan,
                                    buildResult,
                                    packageModeOverride),
                            CommandPackageAttributes::workspacePackage);
                    return timings.measure(
                            "launch workspace packages",
                            () -> workspaceRunPackageService.runPackagedMembers(
                                    plan,
                                    packageResult,
                                    arguments),
                            CommandRunPackageAttributes::workspaceRunPackage);
                },
                CommandRunPackageAttributes::workspaceRunPackage);
        if (result.resolvedLockfile()) {
            spec.commandLine().getOut().println("Resolved workspace dependencies because zolt.lock was missing");
        }
        for (WorkspaceRunPackageResult.MemberRunPackageResult member : result.members()) {
            String output = member.result().javaRunResult().output();
            CommandOutput.printAndFlush(spec, output);
            if (!output.isEmpty() && !output.endsWith("\n")) {
                spec.commandLine().getOut().println();
            }
            spec.commandLine().getOut().println("Ran packaged "
                    + member.result().javaRunResult().mainClass()
                    + " in "
                    + member.member()
                    + " from "
                    + member.result().packageResult().jarPath());
        }
    }

    private void runSinglePackage(
            TimingRecorder timings,
            Optional<PackageMode> packageModeOverride) {
        ProjectConfig config = CommandPackageSupport.withPackageModeOverride(
                timings.measure(
                        "config read",
                        () -> tomlParser.parse(workingDirectory.resolve("zolt.toml"))),
                packageModeOverride);
        lockfiles.requireFreshLockfile(workingDirectory, config, cacheRoot, false);
        RunPackageResult result = timings.measure(
                "run packaged application",
                () -> runPackageService.runPackage(
                        workingDirectory,
                        config,
                        cacheRoot,
                        arguments),
                CommandRunPackageAttributes::runPackage);
        String output = result.javaRunResult().output();
        CommandOutput.printAndFlush(spec, output);
        if (!output.isEmpty() && !output.endsWith("\n")) {
            spec.commandLine().getOut().println();
        }
        spec.commandLine().getOut().println("Ran packaged "
                + result.javaRunResult().mainClass()
                + " from "
                + result.packageResult().jarPath());
    }
}
