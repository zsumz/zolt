package com.zolt.cli.command;

import com.zolt.build.BuildException;
import com.zolt.build.BuildResult;
import com.zolt.build.BuildService;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavacException;
import com.zolt.build.ResourceCopyException;
import com.zolt.build.SourceDiscoveryException;
import com.zolt.cache.ArtifactCacheException;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.CommandProgress;
import com.zolt.cli.ZoltCli;
import com.zolt.cli.console.ProgressWriter;
import com.zolt.framework.FrameworkBuildAugmentationResult;
import com.zolt.framework.FrameworkBuildAugmenter;
import com.zolt.framework.FrameworkBuildException;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.WorkspaceBuildPlan;
import com.zolt.workspace.WorkspaceBuildResult;
import com.zolt.workspace.WorkspaceBuildService;
import com.zolt.workspace.WorkspaceConfigException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "build", description = "Compile main Java sources with the resolved compile classpath.")
public final class BuildCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final BuildService buildService;
    private final WorkspaceBuildService workspaceBuildService;
    private final FrameworkBuildAugmenter frameworkBuildAugmenter;
    private final CommandLockfiles lockfiles;

    @Option(names = "--offline", description = "Use only artifacts already present in the local cache.")
    private boolean offline;

    @Option(names = "--workspace", description = "Build workspace members in dependency order.")
    private boolean workspace;

    @Option(names = "--all", description = "Select every workspace member.")
    private boolean all;

    @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
    private List<String> members = List.of();

    @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
    private List<String> memberGroups = List.of();

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Mixin
    private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

    @Spec
    private CommandSpec spec;

    public BuildCommand() {
        this(CommandFrameworkServices.buildCommandServices());
    }

    private BuildCommand(CommandBuildServices services) {
        this(
                new ZoltTomlParser(),
                services.buildService(),
                services.workspaceBuildService(),
                services.frameworkBuildAugmenter(),
                new CommandLockfiles());
    }

    BuildCommand(
            ZoltTomlParser tomlParser,
            BuildService buildService,
            WorkspaceBuildService workspaceBuildService,
            FrameworkBuildAugmenter frameworkBuildAugmenter,
            CommandLockfiles lockfiles) {
        this.tomlParser = tomlParser;
        this.buildService = buildService;
        this.workspaceBuildService = workspaceBuildService;
        this.frameworkBuildAugmenter = frameworkBuildAugmenter;
        this.lockfiles = lockfiles;
    }

    @Override
    public void run() {
        TimingRecorder timings = CommandTimings.recorder(timingOptions);
        ProgressWriter progress = CommandProgress.human(spec);
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        Path projectRoot = projectDirectory.path();
        try {
            if (workspace) {
                lockfiles.requireFreshWorkspaceLockfile(projectRoot, cacheRoot, offline);
                progress.start("Building workspace");
                WorkspaceBuildResult result = timings.measure(
                        "build workspace",
                        () -> {
                            WorkspaceBuildPlan plan = timings.measure(
                                    "plan workspace build",
                                    () -> workspaceBuildService.planBuild(
                                            projectRoot,
                                            cacheRoot,
                                            offline,
                                            CommandWorkspaceSelections.from(all, members, memberGroups)),
                                    CommandBuildAttributes::workspaceBuildPlan);
                            return timings.measure(
                                    "compile workspace members",
                                    () -> workspaceBuildService.build(plan, cacheRoot),
                                    CommandBuildAttributes::workspaceBuild);
                        },
                        CommandBuildAttributes::workspaceBuild);
                if (result.resolvedLockfile()) {
                    output.detail("Resolved workspace dependencies because zolt.lock was missing");
                }
                for (WorkspaceBuildResult.MemberBuildResult member : result.members()) {
                    output.detail(
                            "Compiled "
                                    + member.result().sourceCount()
                                    + " main source files in "
                                    + member.member());
                }
                output.success("Compiled " + result.sourceCount() + " workspace main source files");
                progress.result("Built " + result.sourceCount() + " workspace main source files");
                return;
            }
            ProjectConfig config = timings.measure(
                    "config read",
                    () -> tomlParser.parse(projectRoot.resolve("zolt.toml")));
            lockfiles.requireFreshLockfile(projectRoot, config, cacheRoot, offline);
            progress.start("Building project");
            output.work("Building " + config.project().name());
            BuildResult result = timings.measure(
                    "compile main",
                    () -> buildService.build(projectRoot, config, cacheRoot, offline),
                    CommandBuildAttributes::build);
            if (result.resolvedLockfile()) {
                output.detail("Resolved dependencies because zolt.lock was missing");
            }
            if (result.mainCompilationSkipped()) {
                output.detail("Skipped main compilation; inputs are unchanged");
            } else {
                output.success("Compiled " + result.sourceCount() + " main source files");
            }
            if (result.resourceCount() > 0) {
                output.detail("Copied " + result.resourceCount() + " resources");
            }
            output.detail("Wrote classes to " + result.outputDirectory());
            progress.result("Built " + result.sourceCount() + " main source files");
            Optional<FrameworkBuildAugmentationResult> augmentationResult =
                    timings.measure(
                            "framework augmentation",
                            () -> frameworkBuildAugmenter.augmentIfEnabled(
                                    projectRoot,
                                    config,
                                    cacheRoot),
                            CommandBuildAttributes::frameworkAugmentation);
            if (augmentationResult.isPresent()) {
                FrameworkBuildAugmentationResult augmentation = augmentationResult.orElseThrow();
                output.detail(
                        "Ran "
                                + augmentation.displayName()
                                + " augmentation; runner jar "
                                + augmentation.runnerJar());
            }
        } catch (BuildException
                | ArtifactCacheException
                | JavacException
                | GroovyCompileException
                | FrameworkBuildException
                | ResourceCopyException
                | SourceDiscoveryException
                | LockfileReadException
                | ResolveException
                | WorkspaceConfigException
                | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        } finally {
            CommandTimings.print(spec, "build", projectRoot, timingOptions, timings);
        }
    }
}
