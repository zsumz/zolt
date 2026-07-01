package com.zolt.cli.command.packaging;

import com.zolt.build.BuildException;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavaRunException;
import com.zolt.build.JavacException;
import com.zolt.build.ManifestGenerationException;
import com.zolt.build.PackageException;
import com.zolt.build.ResourceCopyException;
import com.zolt.build.RunPackageException;
import com.zolt.build.run.RunPackageResult;
import com.zolt.build.run.RunPackageService;
import com.zolt.build.SourceDiscoveryException;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.ZoltCli;
import com.zolt.cli.command.CommandFailures;
import com.zolt.cli.command.CommandFrameworkServices;
import com.zolt.cli.command.CommandLockfiles;
import com.zolt.cli.command.CommandOutput;
import com.zolt.cli.command.CommandProjectDirectory;
import com.zolt.cli.command.CommandServiceBundles.CommandRunPackageServices;
import com.zolt.cli.command.CommandTimings;
import com.zolt.cli.command.CommandWorkspaceSelections;
import com.zolt.cli.command.build.CommandBuildAttributes;
import com.zolt.lockfile.toml.LockfileReadException;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.service.WorkspaceBuildPlan;
import com.zolt.workspace.service.WorkspaceBuildResult;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.packaging.WorkspacePackageResult;
import com.zolt.workspace.packaging.WorkspaceRunPackageResult;
import com.zolt.workspace.packaging.WorkspaceRunPackageService;
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

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Mixin
    private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

    @Spec
    private CommandSpec spec;

    public RunPackageCommand() {
        this(
                new ZoltTomlParser(),
                CommandFrameworkServices.runPackageCommandServices(),
                new CommandLockfiles());
    }

    RunPackageCommand(
            ZoltTomlParser tomlParser,
            CommandRunPackageServices runPackageServices,
            CommandLockfiles lockfiles) {
        this(
                tomlParser,
                runPackageServices.runPackageService(),
                runPackageServices.workspaceRunPackageService(),
                lockfiles);
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
        Path projectRoot = projectDirectory.path();
        try {
            Optional<PackageMode> packageModeOverride = PackageCommandModes.packageModeOverride(mode);
            if (workspace) {
                runWorkspacePackages(projectRoot, timings, packageModeOverride);
                return;
            }
            runSinglePackage(projectRoot, timings, packageModeOverride);
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
            CommandTimings.print(spec, "run-package", projectRoot, timingOptions, timings);
        }
    }

    private void runWorkspacePackages(
            Path projectRoot,
            TimingRecorder timings,
            Optional<PackageMode> packageModeOverride) {
        lockfiles.requireFreshWorkspaceLockfile(projectRoot, cacheRoot, false);
        WorkspaceRunPackageResult result = timings.measure(
                "run workspace packages",
                () -> {
                    WorkspaceBuildPlan plan = timings.measure(
                            "plan workspace run packages",
                            () -> workspaceRunPackageService.planRunPackages(
                                    projectRoot,
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
        CommandHumanOutput humanOutput = CommandHumanOutput.of(spec);
        if (result.resolvedLockfile()) {
            humanOutput.success("Resolved workspace dependencies because zolt.lock was missing");
        }
        for (WorkspaceRunPackageResult.MemberRunPackageResult member : result.members()) {
            String output = member.result().javaRunResult().output();
            CommandOutput.printAndFlush(spec, output);
            if (!output.isEmpty() && !output.endsWith("\n")) {
                humanOutput.blankLine();
            }
            humanOutput.summary("Ran packaged "
                    + member.result().javaRunResult().mainClass()
                    + " in "
                    + member.member());
            humanOutput.pointer("from", member.result().packageResult().jarPath().toString());
        }
    }

    private void runSinglePackage(
            Path projectRoot,
            TimingRecorder timings,
            Optional<PackageMode> packageModeOverride) {
        ProjectConfig config = PackageCommandModes.withPackageModeOverride(
                timings.measure(
                        "config read",
                        () -> tomlParser.parse(projectRoot.resolve("zolt.toml"))),
                packageModeOverride);
        lockfiles.requireFreshLockfile(projectRoot, config, cacheRoot, false);
        RunPackageResult result = timings.measure(
                "run packaged application",
                () -> runPackageService.runPackage(
                        projectRoot,
                        config,
                        cacheRoot,
                        arguments),
                CommandRunPackageAttributes::runPackage);
        String output = result.javaRunResult().output();
        CommandOutput.printAndFlush(spec, output);
        CommandHumanOutput humanOutput = CommandHumanOutput.of(spec);
        if (!output.isEmpty() && !output.endsWith("\n")) {
            humanOutput.blankLine();
        }
        humanOutput.summary("Ran packaged " + result.javaRunResult().mainClass());
        humanOutput.pointer("from", result.packageResult().jarPath().toString());
    }
}
