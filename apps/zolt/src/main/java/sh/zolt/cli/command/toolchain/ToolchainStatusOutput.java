package sh.zolt.cli.command.toolchain;

import sh.zolt.cli.CommandHumanOutput;
import sh.zolt.toolchain.JavaToolchainStatus;
import sh.zolt.toolchain.TestRuntimeToolchain;
import sh.zolt.toolchain.jvm.ResolvedJavaToolchain;
import java.nio.file.Path;
import picocli.CommandLine.Model.CommandSpec;

final class ToolchainStatusOutput {
    private ToolchainStatusOutput() {
    }

    static void printTestRuntime(CommandSpec spec, TestRuntimeToolchain testRuntime) {
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        output.blankLine();
        output.line("test runtime ([toolchain.java.test])");
        output.line("  java: " + testRuntime.request().version());
        output.line("  status: " + (testRuntime.ready() ? "ok" : "error"));
        testRuntime.java().ifPresent(java -> output.line("  resolved: " + java));
        testRuntime.problem().ifPresent(problem -> output.line("  problem: " + problem));
        testRuntime.remediation().ifPresent(next -> output.line("  next: " + next));
    }

    static void print(CommandSpec spec, JavaToolchainStatus status) {
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        ResolvedJavaToolchain resolved = status.resolved();
        output.line("Toolchain");
        output.blankLine();
        output.line("request");
        output.line("  java: " + status.request().version());
        output.line("  source: " + status.requestSource());
        output.line("  distribution: " + status.request().distributionLabel());
        output.line("  features: " + status.request().featuresLabel());
        output.line("  policy: " + status.request().policy().id());
        output.blankLine();
        output.line("resolved");
        output.line("  status: " + (resolved.ok() ? "ok" : "error"));
        output.line("  source: " + resolved.source().label());
        output.line("  javaHome: " + resolved.javaHome().map(Path::toString).orElse("not set"));
        output.line("  java: " + resolved.java().map(Path::toString).orElse("missing"));
        output.line("  javac: " + resolved.javac().map(Path::toString).orElse("missing"));
        output.line("  jar: " + resolved.jar().map(Path::toString).orElse("missing"));
        output.line("  native-image: " + resolved.nativeImage().map(Path::toString).orElse("missing"));
        output.line("  version: " + resolved.runtime().version().orElse("unknown"));
        output.line("  vendor: " + resolved.runtime().vendor().orElse("unknown"));
        for (String note : resolved.notes()) {
            output.line("  note: " + note);
        }
    }
}
