package com.zolt.cli.command.packaging;

import com.zolt.build.BuildException;
import com.zolt.build.BuildResultWithClasspaths;
import com.zolt.build.BuildService;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavacException;
import com.zolt.build.ManifestGenerationException;
import com.zolt.build.PackageException;
import com.zolt.build.packageplan.PackagePlan;
import com.zolt.build.packageplan.PackagePlanFormatter;
import com.zolt.build.packageplan.PackagePlanService;
import com.zolt.build.packaging.PackageResult;
import com.zolt.build.packaging.PackageService;
import com.zolt.build.ResourceCopyException;
import com.zolt.build.SourceDiscoveryException;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.CommandProgress;
import com.zolt.cli.ZoltCli;
import com.zolt.cli.command.*;
import com.zolt.cli.command.CommandServiceBundles.CommandPackageServices;
import com.zolt.cli.command.build.CommandBuildAttributes;
import com.zolt.cli.console.ProgressWriter;
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
import com.zolt.workspace.packaging.WorkspacePackageService;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "package", description = "Package compiled classes into a jar.")
public final class PackageCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final PackagePlanService packagePlanService;
    private final PackagePlanFormatter packagePlanFormatter;
    private final PackageService packageService;
    private final BuildService buildService;
    private final WorkspacePackageService workspacePackageService;
    private final CommandLockfiles lockfiles;
    private final CommandPackageResultWriter packageResultWriter;

    @Option(names = "--workspace", description = "Package workspace members in dependency order.")
    private boolean workspace;

    @Option(names = "--all", description = "Select every workspace member.")
    private boolean all;

    @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
    private List<String> members = List.of();

    @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
    private List<String> memberGroups = List.of();

    @Option(names = "--mode", description = "Package mode: thin, spring-boot, war, spring-boot-war, quarkus, or uber.")
    private String mode;

    @Option(names = "--plan", description = "Print the package content plan without building or writing the archive.")
    private boolean planOnly;

    @Option(names = "--format", description = "Package plan output format: text or json.")
    private String format = "text";

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Mixin
    private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

    @Spec
    private CommandSpec spec;

    public PackageCommand() {
        this(
                new ZoltTomlParser(),
                new PackagePlanFormatter(),
                CommandFrameworkServices.packageCommandServices(),
                new CommandPackageResultWriter(),
                new CommandLockfiles());
    }

    PackageCommand(
            ZoltTomlParser tomlParser,
            PackagePlanFormatter packagePlanFormatter,
            CommandPackageServices packageServices,
            CommandPackageResultWriter packageResultWriter,
            CommandLockfiles lockfiles) {
        this(
                tomlParser,
                packageServices.packagePlanService(),
                packagePlanFormatter,
                packageServices.packageService(),
                packageServices.buildService(),
                packageServices.workspacePackageService(),
                packageResultWriter,
                lockfiles);
    }

    PackageCommand(
            ZoltTomlParser tomlParser,
            PackagePlanService packagePlanService,
            PackagePlanFormatter packagePlanFormatter,
            PackageService packageService,
            BuildService buildService,
            WorkspacePackageService workspacePackageService,
            CommandLockfiles lockfiles) {
        this(
                tomlParser,
                packagePlanService,
                packagePlanFormatter,
                packageService,
                buildService,
                workspacePackageService,
                new CommandPackageResultWriter(),
                lockfiles);
    }

    PackageCommand(
            ZoltTomlParser tomlParser,
            PackagePlanService packagePlanService,
            PackagePlanFormatter packagePlanFormatter,
            PackageService packageService,
            BuildService buildService,
            WorkspacePackageService workspacePackageService,
            CommandPackageResultWriter packageResultWriter,
            CommandLockfiles lockfiles) {
        this.tomlParser = tomlParser;
        this.packagePlanService = packagePlanService;
        this.packagePlanFormatter = packagePlanFormatter;
        this.packageService = packageService;
        this.buildService = buildService;
        this.workspacePackageService = workspacePackageService;
        this.packageResultWriter = packageResultWriter;
        this.lockfiles = lockfiles;
    }

    @Override
    public void run() {
        TimingRecorder timings = CommandTimings.recorder(timingOptions);
        Path projectRoot = projectDirectory.path();
        try {
            Optional<PackageMode> packageModeOverride = PackageCommandModes.packageModeOverride(mode);
            PackageCommandModes.PlanOutputFormat planOutputFormat = PackageCommandModes.planOutputFormat(format);
            if (!planOnly && planOutputFormat != PackageCommandModes.PlanOutputFormat.TEXT) {
                throw PackageException.actionable(
                        "Package --format is only supported with --plan.",
                        "Use `zolt package --plan --format json`.");
            }
            if (workspace) {
                runWorkspacePackage(projectRoot, timings, packageModeOverride, planOnly);
                return;
            }
            runSingleProjectPackage(projectRoot, timings, packageModeOverride, planOutputFormat);
        } catch (BuildException
                | JavacException
                | GroovyCompileException
                | ManifestGenerationException
                | PackageException
                | ResourceCopyException
                | SourceDiscoveryException
                | LockfileReadException
                | ResolveException
                | WorkspaceConfigException
                | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        } finally {
            CommandTimings.print(spec, "package", projectRoot, timingOptions, timings);
        }
    }

    private void runWorkspacePackage(
            Path projectRoot,
            TimingRecorder timings,
            Optional<PackageMode> packageModeOverride,
            boolean planOnly) {
        if (planOnly) {
            throw PackageException.actionable(
                    "Package --plan is currently single-project.",
                    "Run it from the member project you want to inspect.");
        }
        lockfiles.requireFreshWorkspaceLockfile(projectRoot, cacheRoot, false);
        ProgressWriter progress = CommandProgress.human(spec);
        progress.start("Packaging workspace");
        WorkspacePackageResult result = timings.measure(
                "package workspace",
                () -> {
                    WorkspaceBuildPlan plan = timings.measure(
                            "plan workspace packages",
                            () -> workspacePackageService.planPackages(
                                    projectRoot,
                                    cacheRoot,
                                    CommandWorkspaceSelections.from(all, members, memberGroups)),
                            CommandBuildAttributes::workspaceBuildPlan);
                    WorkspaceBuildResult buildResult = timings.measure(
                            "build workspace package inputs",
                            () -> workspacePackageService.buildPackageInputs(plan, cacheRoot),
                            CommandBuildAttributes::workspaceBuild);
                    return timings.measure(
                            "assemble workspace packages",
                            () -> workspacePackageService.packageBuiltJars(
                                    plan,
                                    buildResult,
                                    packageModeOverride),
                            CommandPackageAttributes::workspacePackage);
                },
                CommandPackageAttributes::workspacePackage);
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        if (result.resolvedLockfile()) {
            output.success("Resolved workspace dependencies because zolt.lock was missing");
        }
        for (WorkspacePackageResult.MemberPackageResult member : result.members()) {
            printPackageResult(member.result(), " in " + member.member());
        }
        output.success("Packaged " + result.members().size() + " workspace members");
        progress.result("Packaged " + result.members().size() + " workspace members");
    }

    private void runSingleProjectPackage(
            Path projectRoot,
            TimingRecorder timings,
            Optional<PackageMode> packageModeOverride,
            PackageCommandModes.PlanOutputFormat planOutputFormat) {
        ProjectConfig config = PackageCommandModes.withPackageModeOverride(
                timings.measure(
                        "config read",
                        () -> tomlParser.parse(projectRoot.resolve("zolt.toml"))),
                packageModeOverride);
        if (!planOnly) {
            if (packageModeOverride.isPresent()) {
                lockfiles.refreshExistingLockfile(projectRoot, config, cacheRoot, false);
            }
            packageService.preparePackageToolingIfNeeded(projectRoot, config, cacheRoot);
        }
        lockfiles.requireFreshLockfile(projectRoot, config, cacheRoot, false);
        if (planOnly) {
            PackagePlan packagePlan = timings.measure(
                    "plan package contents",
                    () -> packagePlanService.plan(projectRoot, config),
                    CommandPackageAttributes::packagePlan);
            if (planOutputFormat == PackageCommandModes.PlanOutputFormat.JSON) {
                CommandOutput.printAndFlush(spec, packagePlanFormatter.json(packagePlan));
            } else {
                CommandOutput.printAndFlush(spec, packagePlanFormatter.text(packagePlan));
            }
            return;
        }
        ProgressWriter progress = CommandProgress.human(spec);
        progress.start("Packaging project");
        PackageResult result = timings.measure(
                "package",
                () -> {
                    BuildResultWithClasspaths buildResult = timings.measure(
                            "build package inputs",
                            () -> buildService.buildWithClasspaths(
                                    projectRoot,
                                    config,
                                    cacheRoot,
                                    false),
                            resultWithClasspaths -> CommandBuildAttributes.build(resultWithClasspaths.buildResult()));
                    return timings.measure(
                            "assemble package",
                            () -> packageService.packageJar(projectRoot, config, buildResult, cacheRoot),
                            CommandPackageAttributes::packageResult);
                },
                CommandPackageAttributes::packageResult);
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        if (result.buildResult().resolvedLockfile()) {
            output.success("Resolved dependencies because zolt.lock was missing");
        }
        printPackageResult(result, "");
        progress.result("Packaged " + result.jarPath());
    }

    private void printPackageResult(PackageResult result, String suffix) {
        packageResultWriter.print(CommandHumanOutput.of(spec), result, suffix);
    }
}
