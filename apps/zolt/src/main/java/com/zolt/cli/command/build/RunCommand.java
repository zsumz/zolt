package com.zolt.cli.command.build;

import com.zolt.build.BuildException;
import com.zolt.build.GroovyCompileException;
import com.zolt.build.JavaRunException;
import com.zolt.build.JavacException;
import com.zolt.build.ResourceCopyException;
import com.zolt.build.RunException;
import com.zolt.build.run.RunResult;
import com.zolt.build.run.RunService;
import com.zolt.build.SourceDiscoveryException;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.ZoltCli;
import com.zolt.cli.command.CommandFailures;
import com.zolt.cli.command.CommandFrameworkServices;
import com.zolt.cli.command.CommandLockfiles;
import com.zolt.cli.command.CommandOutput;
import com.zolt.cli.command.CommandProjectDirectory;
import com.zolt.cli.command.CommandServiceBundles.CommandRunServices;
import com.zolt.cli.command.CommandTimings;
import com.zolt.cli.command.CommandWorkspaceSelections;
import com.zolt.framework.FrameworkRunException;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.WorkspaceBuildPlan;
import com.zolt.workspace.WorkspaceBuildResult;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.run.WorkspaceRunResult;
import com.zolt.workspace.run.WorkspaceRunService;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "run", description = "Build and run the configured main class.")
public final class RunCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final RunService runService;
    private final WorkspaceRunService workspaceRunService;
    private final CommandLockfiles lockfiles;

    @Parameters(arity = "0..*", paramLabel = "ARGS", description = "Arguments passed to the application after `--`.")
    private List<String> arguments = List.of();

    @Option(names = "--workspace", description = "Run workspace members in dependency order.")
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

    public RunCommand() {
        this(
                new ZoltTomlParser(),
                CommandFrameworkServices.runCommandServices(),
                new CommandLockfiles());
    }

    RunCommand(
            ZoltTomlParser tomlParser,
            CommandRunServices runServices,
            CommandLockfiles lockfiles) {
        this(
                tomlParser,
                runServices.runService(),
                runServices.workspaceRunService(),
                lockfiles);
    }

    RunCommand(
            ZoltTomlParser tomlParser,
            RunService runService,
            WorkspaceRunService workspaceRunService,
            CommandLockfiles lockfiles) {
        this.tomlParser = tomlParser;
        this.runService = runService;
        this.workspaceRunService = workspaceRunService;
        this.lockfiles = lockfiles;
    }

    @Override
    public void run() {
        TimingRecorder timings = CommandTimings.recorder(timingOptions);
        Path projectRoot = projectDirectory.path();
        try {
            if (workspace) {
                lockfiles.requireFreshWorkspaceLockfile(projectRoot, cacheRoot, false);
                WorkspaceRunResult result = timings.measure(
                        "run workspace",
                        () -> {
                            WorkspaceBuildPlan plan = timings.measure(
                                    "plan workspace run",
                                    () -> workspaceRunService.planRun(
                                            projectRoot,
                                            cacheRoot,
                                            CommandWorkspaceSelections.from(all, members, memberGroups)),
                                    CommandBuildAttributes::workspaceBuildPlan);
                            WorkspaceBuildResult buildResult = timings.measure(
                                    "build workspace run inputs",
                                    () -> workspaceRunService.buildRunInputs(plan, cacheRoot),
                                    CommandBuildAttributes::workspaceBuild);
                            return timings.measure(
                                    "launch workspace members",
                                    () -> workspaceRunService.runBuiltMembers(
                                            plan,
                                            buildResult,
                                            arguments,
                                            output -> CommandOutput.printAndFlush(spec, output)),
                                    CommandRunAttributes::workspaceRun);
                        },
                        CommandRunAttributes::workspaceRun);
                CommandHumanOutput output = CommandHumanOutput.of(spec);
                if (result.resolvedLockfile()) {
                    output.success("Resolved workspace dependencies because zolt.lock was missing");
                }
                for (WorkspaceRunResult.MemberRunResult member : result.members()) {
                    String processOutput = member.result().javaRunResult().output();
                    if (!processOutput.isEmpty() && !processOutput.endsWith("\n")) {
                        output.blankLine();
                    }
                    output.success("Ran "
                            + member.result().javaRunResult().mainClass()
                            + " in "
                            + member.member());
                }
                return;
            }
            ProjectConfig config = timings.measure(
                    "config read",
                    () -> tomlParser.parse(projectRoot.resolve("zolt.toml")));
            lockfiles.requireFreshLockfile(projectRoot, config, cacheRoot, false);
            RunResult result = timings.measure(
                    "run application",
                    () -> runService.run(
                            projectRoot,
                            config,
                            cacheRoot,
                            arguments,
                            output -> CommandOutput.printAndFlush(spec, output)),
                    CommandRunAttributes::run);
            CommandHumanOutput output = CommandHumanOutput.of(spec);
            String processOutput = result.javaRunResult().output();
            if (!processOutput.isEmpty() && !processOutput.endsWith("\n")) {
                output.blankLine();
            }
            output.success("Ran " + result.javaRunResult().mainClass());
        } catch (JavaRunException exception) {
            throw CommandFailures.user(spec, firstLine(exception.getMessage()), exception);
        } catch (BuildException
                | JavacException
                | GroovyCompileException
                | ResourceCopyException
                | RunException
                | FrameworkRunException
                | SourceDiscoveryException
                | LockfileReadException
                | ResolveException
                | WorkspaceConfigException
                | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        } finally {
            CommandTimings.print(spec, "run", projectRoot, timingOptions, timings);
        }
    }

    private static String firstLine(String value) {
        int newline = value.indexOf('\n');
        return newline < 0 ? value : value.substring(0, newline);
    }
}
