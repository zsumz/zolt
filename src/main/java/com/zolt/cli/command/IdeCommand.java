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
        mixinStandardHelpOptions = true,
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

        @Option(names = "--cwd", hidden = true)
        private Path workingDirectory = Path.of(".");

        @Option(names = "--cache-root", hidden = true)
        private Path cacheRoot = LocalArtifactCache.defaultRoot();

        @Mixin
        private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            TimingRecorder timings = CommandTimings.recorder(timingOptions);
            try {
                if (workspace) {
                    WorkspaceIdeModel model = timings.measure(
                            "ide model export",
                            () -> new WorkspaceIdeModelService().export(
                                    workingDirectory,
                                    cacheRoot,
                                    true,
                                    offline,
                                    timings),
                            IdeCommand::workspaceIdeModelAttributes);
                    String output = timings.measure(
                            "ide model json",
                            () -> new WorkspaceIdeModelJsonWriter().write(model));
                    CommandOutput.printAndFlush(spec, output);
                    return;
                }
                IdeModel model = timings.measure(
                        "ide model export",
                        () -> new IdeModelService().export(
                                workingDirectory,
                                cacheRoot,
                                true,
                                offline,
                                timings),
                        IdeCommand::ideModelAttributes);
                String output = timings.measure(
                        "ide model json",
                        () -> new IdeModelJsonWriter().write(model));
                CommandOutput.printAndFlush(spec, output);
            } finally {
                CommandTimings.print(spec, "ide model", workingDirectory, timingOptions, timings);
            }
        }
    }

    private static Map<String, String> ideModelAttributes(IdeModel model) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("sourceRoots", Integer.toString(model.sourceRoots().size()));
        attributes.put("resourceRoots", Integer.toString(model.resourceRoots().size()));
        attributes.put("compileClasspathEntries", Integer.toString(model.classpaths().compile().size()));
        attributes.put("runtimeClasspathEntries", Integer.toString(model.classpaths().runtime().size()));
        attributes.put("testClasspathEntries", Integer.toString(model.classpaths().test().size()));
        attributes.put("diagnostics", Integer.toString(model.diagnostics().size()));
        return attributes;
    }

    private static Map<String, String> workspaceIdeModelAttributes(WorkspaceIdeModel model) {
        return Map.of(
                "projects", Integer.toString(model.projects().size()),
                "edges", Integer.toString(model.edges().size()),
                "diagnostics", Integer.toString(model.diagnostics().size()));
    }
}
