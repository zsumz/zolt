package com.zolt.cli.command.build;

import com.zolt.build.CleanException;
import com.zolt.build.CleanResult;
import com.zolt.build.CleanService;
import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.command.CommandFailures;
import com.zolt.cli.command.CommandProjectDirectory;
import com.zolt.cli.command.CommandWorkspaceSelections;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.WorkspaceCleanResult;
import com.zolt.workspace.WorkspaceCleanService;
import com.zolt.workspace.WorkspaceConfigException;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "clean", description = "Remove project build output.")
public final class CleanCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final CleanService cleanService;
    private final WorkspaceCleanService workspaceCleanService;

    @Option(names = "--workspace", description = "Clean workspace members in dependency order.")
    private boolean workspace;

    @Option(names = "--all", description = "Select every workspace member.")
    private boolean all;

    @Option(names = "--member", description = "Select a workspace member by declared path. May be repeated.")
    private List<String> members = List.of();

    @Option(names = "--members", split = ",", description = "Select comma-separated workspace members by declared path.")
    private List<String> memberGroups = List.of();

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Spec
    private CommandSpec spec;

    public CleanCommand() {
        this(new ZoltTomlParser(), new CleanService(), new WorkspaceCleanService());
    }

    CleanCommand(
            ZoltTomlParser tomlParser,
            CleanService cleanService,
            WorkspaceCleanService workspaceCleanService) {
        this.tomlParser = tomlParser;
        this.cleanService = cleanService;
        this.workspaceCleanService = workspaceCleanService;
    }

    @Override
    public void run() {
        try {
            Path projectRoot = projectDirectory.path();
            if (workspace) {
                WorkspaceCleanResult result = workspaceCleanService.clean(
                        projectRoot,
                        CommandWorkspaceSelections.from(all, members, memberGroups));
                printWorkspaceResult(result);
                return;
            }
            ProjectConfig config = tomlParser.parse(projectRoot.resolve("zolt.toml"));
            CleanResult result = cleanService.clean(projectRoot, config);
            CommandHumanOutput output = CommandHumanOutput.of(spec);
            if (result.deletedPaths().isEmpty()) {
                output.detail("Nothing to clean");
                return;
            }
            output.success("Deleted " + result.deletedCount() + " build output paths");
            for (Path path : result.deletedPaths()) {
                output.success("Deleted " + path);
            }
        } catch (CleanException | WorkspaceConfigException | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private void printWorkspaceResult(WorkspaceCleanResult result) {
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        if (result.deletedCount() == 0) {
            output.detail("Nothing to clean");
            return;
        }
        output.success("Deleted "
                + result.deletedCount()
                + " workspace build output paths across "
                + result.members().size()
                + " members");
        for (WorkspaceCleanResult.MemberCleanResult member : result.members()) {
            for (Path path : member.result().deletedPaths()) {
                output.success("Deleted " + member.member() + " " + path);
            }
        }
    }
}
