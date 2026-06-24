package com.zolt.cli.command;

import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.ZoltCli;
import com.zolt.ide.IdeModel;
import com.zolt.ide.IdeModelJsonWriter;
import com.zolt.ide.IdeModelService;
import com.zolt.ide.WorkspaceIdeModel;
import com.zolt.ide.WorkspaceIdeModelJsonWriter;
import com.zolt.ide.WorkspaceIdeModelService;
import com.zolt.perf.TimingRecorder;
import com.zolt.quarkus.QuarkusIdeFrameworkModelProvider;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "ide",
        description = "Export project models for IDE and tooling integrations.",
        subcommands = {
                IdeCommand.ModelCommand.class
        })
public final class IdeCommand implements Runnable {
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    @Command(name = "model", description = "Export the Zolt project model.")
    public static final class ModelCommand implements Runnable {
        private final WorkspaceIdeModelService workspaceIdeModelService;
        private final WorkspaceIdeModelJsonWriter workspaceIdeModelJsonWriter;
        private final IdeModelService ideModelService;
        private final IdeModelJsonWriter ideModelJsonWriter;

        enum Format {
            JSON
        }

        @Option(names = "--format", required = true, description = "Output format: json.")
        private Format format;

        @Option(names = "--check-lock", description = "Report whether zolt.lock is stale without rewriting it.")
        private boolean checkLock;

        @Option(names = "--offline", description = "Use only artifacts already present in the local cache when checking zolt.lock.")
        private boolean offline;

        @Option(names = "--workspace", description = "Export the discovered workspace model.")
        private boolean workspace;

        @Mixin
        private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = LocalArtifactCache.defaultRoot();

        @Mixin
        private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

        @Spec
        private CommandSpec spec;

        public ModelCommand() {
            this(
                    workspaceIdeModelService(),
                    new WorkspaceIdeModelJsonWriter(),
                    ideModelService(),
                    new IdeModelJsonWriter());
        }

        private static IdeModelService ideModelService() {
            return new IdeModelService(new QuarkusIdeFrameworkModelProvider());
        }

        private static WorkspaceIdeModelService workspaceIdeModelService() {
            return new WorkspaceIdeModelService(ideModelService());
        }

        ModelCommand(
                WorkspaceIdeModelService workspaceIdeModelService,
                WorkspaceIdeModelJsonWriter workspaceIdeModelJsonWriter,
                IdeModelService ideModelService,
                IdeModelJsonWriter ideModelJsonWriter) {
            this.workspaceIdeModelService = workspaceIdeModelService;
            this.workspaceIdeModelJsonWriter = workspaceIdeModelJsonWriter;
            this.ideModelService = ideModelService;
            this.ideModelJsonWriter = ideModelJsonWriter;
        }

        @Override
        public void run() {
            TimingRecorder timings = CommandTimings.recorder(timingOptions);
            Path projectRoot = projectDirectory.path();
            try {
                if (workspace) {
                    WorkspaceIdeModel model = timings.measure(
                            "ide model export",
                            () -> workspaceIdeModelService.export(
                                    projectRoot,
                                    cacheRoot,
                                    true,
                                    offline,
                                    timings),
                            IdeCommand::workspaceIdeModelAttributes);
                    String output = timings.measure(
                            "ide model json",
                            () -> workspaceIdeModelJsonWriter.write(model));
                    CommandOutput.printAndFlush(spec, output);
                    return;
                }
                IdeModel model = timings.measure(
                        "ide model export",
                        () -> ideModelService.export(
                                projectRoot,
                                cacheRoot,
                                true,
                                offline,
                                timings),
                        IdeCommand::ideModelAttributes);
                String output = timings.measure(
                        "ide model json",
                        () -> ideModelJsonWriter.write(model));
                CommandOutput.printAndFlush(spec, output);
            } finally {
                CommandTimings.print(spec, "ide model", projectRoot, timingOptions, timings);
            }
        }
    }

    private static Map<String, String> ideModelAttributes(IdeModel model) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put(CommandAttributeKeys.SOURCE_ROOTS, Integer.toString(model.sourceRoots().size()));
        attributes.put(CommandAttributeKeys.RESOURCE_ROOTS, Integer.toString(model.resourceRoots().size()));
        attributes.put(CommandAttributeKeys.COMPILE_CLASSPATH_ENTRIES, Integer.toString(model.classpaths().compile().size()));
        attributes.put(CommandAttributeKeys.RUNTIME_CLASSPATH_ENTRIES, Integer.toString(model.classpaths().runtime().size()));
        attributes.put(CommandAttributeKeys.TEST_CLASSPATH_ENTRIES, Integer.toString(model.classpaths().test().size()));
        attributes.put(CommandAttributeKeys.DIAGNOSTICS, Integer.toString(model.diagnostics().size()));
        return attributes;
    }

    private static Map<String, String> workspaceIdeModelAttributes(WorkspaceIdeModel model) {
        return Map.of(
                CommandAttributeKeys.PROJECTS, Integer.toString(model.projects().size()),
                CommandAttributeKeys.EDGES, Integer.toString(model.edges().size()),
                CommandAttributeKeys.DIAGNOSTICS, Integer.toString(model.diagnostics().size()));
    }
}
