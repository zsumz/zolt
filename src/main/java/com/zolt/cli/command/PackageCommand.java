package com.zolt.cli.command;

import com.zolt.build.BuildException;
import com.zolt.build.BuildResultWithClasspaths;
import com.zolt.build.BuildService;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavacException;
import com.zolt.build.ManifestGenerationException;
import com.zolt.build.PackageArtifact;
import com.zolt.build.PackageException;
import com.zolt.build.PackagePlan;
import com.zolt.build.PackagePlanFormatter;
import com.zolt.build.PackagePlanService;
import com.zolt.build.PackageResult;
import com.zolt.build.PackageService;
import com.zolt.build.ResourceCopyException;
import com.zolt.build.SourceDiscoveryException;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.ZoltCli;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.quarkus.QuarkusPackageAugmenter;
import com.zolt.resolve.ResolveException;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.WorkspaceBuildPlan;
import com.zolt.workspace.WorkspaceBuildResult;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspacePackageResult;
import com.zolt.workspace.WorkspacePackageService;
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

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Mixin
    private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

    @Spec
    private CommandSpec spec;

    public PackageCommand() {
        this(
                new ZoltTomlParser(),
                new PackagePlanService(),
                new PackagePlanFormatter(),
                new PackageService(new QuarkusPackageAugmenter()),
                new BuildService(),
                new WorkspacePackageService(),
                new CommandLockfiles());
    }

    PackageCommand(
            ZoltTomlParser tomlParser,
            PackagePlanService packagePlanService,
            PackagePlanFormatter packagePlanFormatter,
            PackageService packageService,
            BuildService buildService,
            WorkspacePackageService workspacePackageService,
            CommandLockfiles lockfiles) {
        this.tomlParser = tomlParser;
        this.packagePlanService = packagePlanService;
        this.packagePlanFormatter = packagePlanFormatter;
        this.packageService = packageService;
        this.buildService = buildService;
        this.workspacePackageService = workspacePackageService;
        this.lockfiles = lockfiles;
    }

    @Override
    public void run() {
        TimingRecorder timings = CommandTimings.recorder(timingOptions);
        try {
            Optional<PackageMode> packageModeOverride = CommandPackageSupport.packageModeOverride(mode);
            CommandPackageSupport.PlanOutputFormat planOutputFormat = CommandPackageSupport.planOutputFormat(format);
            if (!planOnly && planOutputFormat != CommandPackageSupport.PlanOutputFormat.TEXT) {
                throw new PackageException("Package --format is only supported with --plan. Use `zolt package --plan --format json`.");
            }
            if (workspace) {
                runWorkspacePackage(timings, packageModeOverride, planOnly);
                return;
            }
            runSingleProjectPackage(timings, packageModeOverride, planOutputFormat);
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
            CommandTimings.print(spec, "package", workingDirectory, timingOptions, timings);
        }
    }

    private void runWorkspacePackage(
            TimingRecorder timings,
            Optional<PackageMode> packageModeOverride,
            boolean planOnly) {
        if (planOnly) {
            throw new PackageException("Package --plan is currently single-project. Run it from the member project you want to inspect.");
        }
        lockfiles.requireFreshWorkspaceLockfile(workingDirectory, cacheRoot, false);
        WorkspacePackageResult result = timings.measure(
                "package workspace",
                () -> {
                    WorkspaceBuildPlan plan = timings.measure(
                            "plan workspace packages",
                            () -> workspacePackageService.planPackages(
                                    workingDirectory,
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
        if (result.resolvedLockfile()) {
            spec.commandLine().getOut().println("Resolved workspace dependencies because zolt.lock was missing");
        }
        for (WorkspacePackageResult.MemberPackageResult member : result.members()) {
            printPackageResult(member.result(), " in " + member.member());
        }
        spec.commandLine().getOut().println(
                "Packaged "
                        + result.members().size()
                        + " workspace members");
    }

    private void runSingleProjectPackage(
            TimingRecorder timings,
            Optional<PackageMode> packageModeOverride,
            CommandPackageSupport.PlanOutputFormat planOutputFormat) {
        ProjectConfig config = CommandPackageSupport.withPackageModeOverride(
                timings.measure(
                        "config read",
                        () -> tomlParser.parse(workingDirectory.resolve("zolt.toml"))),
                packageModeOverride);
        lockfiles.requireFreshLockfile(workingDirectory, config, cacheRoot, false);
        if (planOnly) {
            PackagePlan packagePlan = timings.measure(
                    "plan package contents",
                    () -> packagePlanService.plan(workingDirectory, config),
                    CommandPackageAttributes::packagePlan);
            if (planOutputFormat == CommandPackageSupport.PlanOutputFormat.JSON) {
                CommandOutput.printAndFlush(spec, packagePlanFormatter.json(packagePlan));
            } else {
                CommandOutput.printAndFlush(spec, packagePlanFormatter.text(packagePlan));
            }
            return;
        }
        PackageResult result = timings.measure(
                "package",
                () -> {
                    packageService.preparePackageToolingIfNeeded(workingDirectory, config, cacheRoot);
                    BuildResultWithClasspaths buildResult = timings.measure(
                            "build package inputs",
                            () -> buildService.buildWithClasspaths(
                                    workingDirectory,
                                    config,
                                    cacheRoot,
                                    false),
                            resultWithClasspaths -> CommandBuildAttributes.build(resultWithClasspaths.buildResult()));
                    return timings.measure(
                            "assemble package",
                            () -> packageService.packageJar(workingDirectory, config, buildResult, cacheRoot),
                            CommandPackageAttributes::packageResult);
                },
                CommandPackageAttributes::packageResult);
        if (result.buildResult().resolvedLockfile()) {
            spec.commandLine().getOut().println("Resolved dependencies because zolt.lock was missing");
        }
        printPackageResult(result, "");
    }

    private void printPackageResult(PackageResult result, String suffix) {
        spec.commandLine().getOut().println(CommandPackageSupport.packageSummary(result) + suffix);
        if (result.hasMainClass()) {
            spec.commandLine().getOut().println(suffix.isBlank()
                    ? "Included Main-Class manifest entry"
                    : "Included Main-Class manifest entry" + suffix);
            if (suffix.isBlank()) {
                spec.commandLine().getOut().println("Run with: java -jar " + result.jarPath());
                if (result.mode() == PackageMode.SPRING_BOOT) {
                    spec.commandLine().getOut().println("Run with Zolt: zolt run-package --mode spring-boot -- [args]");
                } else if (result.mode() == PackageMode.SPRING_BOOT_WAR) {
                    spec.commandLine().getOut().println("Run with Zolt: zolt run-package --mode spring-boot-war -- [args]");
                } else if (result.mode() == PackageMode.QUARKUS) {
                    spec.commandLine().getOut().println("Run with Zolt: zolt run");
                } else {
                    spec.commandLine().getOut().println("Run with dependencies: zolt run-package -- [args]");
                }
            }
        } else if (suffix.isBlank() && result.mode() == PackageMode.WAR) {
            spec.commandLine().getOut().println("WAR is a servlet container deployment artifact; use `spring-boot-war` for java -jar.");
        } else if (suffix.isBlank()) {
            spec.commandLine().getOut().println("No Main-Class manifest entry; add [project].main to make the jar directly runnable.");
        }
        if (suffix.isBlank()) {
            printPackageModeDetail(result);
        }
        spec.commandLine().getOut().println("Wrote archive to " + result.jarPath());
        result.evidenceManifestPath().ifPresent(path ->
                spec.commandLine().getOut().println("Wrote package evidence to " + path));
        for (PackageArtifact artifact : result.artifacts()) {
            spec.commandLine().getOut().println(
                    "Wrote "
                            + artifact.classifier()
                            + " jar to "
                            + artifact.path());
        }
    }

    private void printPackageModeDetail(PackageResult result) {
        if (result.mode() == PackageMode.SPRING_BOOT) {
            spec.commandLine().getOut().println("Spring Boot jar: dependencies are nested under BOOT-INF/lib.");
        } else if (result.mode() == PackageMode.WAR) {
            spec.commandLine().getOut().println("WAR: application classes are under WEB-INF/classes and runtime dependencies are under WEB-INF/lib.");
        } else if (result.mode() == PackageMode.SPRING_BOOT_WAR) {
            spec.commandLine().getOut().println("Spring Boot WAR: runtime dependencies are under WEB-INF/lib and provided dependencies are under WEB-INF/lib-provided.");
        } else if (result.mode() == PackageMode.QUARKUS) {
            spec.commandLine().getOut().println("Quarkus fast-jar: deploy the whole target/quarkus-app directory.");
        } else {
            spec.commandLine().getOut().println("Thin jar: dependencies are not bundled.");
            result.runtimeClasspathPath().ifPresent(path ->
                    spec.commandLine().getOut().println("Wrote runtime classpath to " + path));
        }
    }
}
