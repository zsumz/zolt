package sh.zolt.cli.command.toolchain;

import sh.zolt.cli.CommandHumanOutput;
import sh.zolt.cli.command.CommandFailures;
import sh.zolt.error.ActionableException;
import sh.zolt.project.toolchain.JavaFeature;
import sh.zolt.toolchain.catalog.BundledJavaToolchainCatalog;
import sh.zolt.toolchain.catalog.JavaToolchainCatalog;
import sh.zolt.toolchain.lock.LockedJavaToolchain;
import sh.zolt.toolchain.store.ToolchainStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "available", description = "Show Java toolchains available from Zolt's bundled catalog.")
public final class ToolchainAvailableCommand implements Callable<Integer> {
    private final JavaToolchainCatalog catalog;

    @Option(names = "--install-root", hidden = true)
    private Path installRoot;

    @Spec
    private CommandSpec spec;

    public ToolchainAvailableCommand() {
        this(new BundledJavaToolchainCatalog());
    }

    ToolchainAvailableCommand(JavaToolchainCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Integer call() {
        try {
            print(catalog.available(), new ToolchainStore(installRoot));
            return 0;
        } catch (ActionableException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private void print(List<LockedJavaToolchain> toolchains, ToolchainStore store) {
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        output.line("Available Java toolchains");
        output.blankLine();
        Map<Lane, ArrayList<LockedJavaToolchain>> lanes = lanes(toolchains);
        for (Map.Entry<Lane, ArrayList<LockedJavaToolchain>> entry : lanes.entrySet()) {
            Lane lane = entry.getKey();
            output.line(lane.distribution + " " + lane.requestedVersion + " -> " + lane.resolvedVersion);
            output.line("  features: " + lane.features);
            for (LockedJavaToolchain locked : entry.getValue()) {
                String status = store.installed(locked)
                        ? "installed at " + store.javaHome(locked)
                        : "available";
                output.line("  " + locked.platform().id() + ": " + status);
            }
            output.blankLine();
        }
    }

    private static Map<Lane, ArrayList<LockedJavaToolchain>> lanes(List<LockedJavaToolchain> toolchains) {
        Map<Lane, ArrayList<LockedJavaToolchain>> lanes = new LinkedHashMap<>();
        for (LockedJavaToolchain locked : toolchains) {
            lanes.computeIfAbsent(Lane.from(locked), ignored -> new ArrayList<>()).add(locked);
        }
        return lanes;
    }

    private record Lane(
            String distribution,
            String requestedVersion,
            String resolvedVersion,
            String features) {
        static Lane from(LockedJavaToolchain locked) {
            return new Lane(
                    locked.resolvedDistribution().id(),
                    locked.request().version(),
                    locked.resolvedVersion(),
                    locked.request().features().isEmpty()
                            ? "none"
                            : String.join(", ", locked.request().features().stream()
                                    .map(JavaFeature::id)
                                    .sorted()
                                    .toList()));
        }
    }
}
