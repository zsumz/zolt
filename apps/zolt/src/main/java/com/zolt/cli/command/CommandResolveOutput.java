package com.zolt.cli.command;

import com.zolt.cli.CommandHumanOutput;
import com.zolt.resolve.ResolveResult;
import picocli.CommandLine.Model.CommandSpec;

public final class CommandResolveOutput {
    private CommandResolveOutput() {
    }

    public static void print(CommandSpec spec, ResolveResult result) {
        print(spec, result, true);
    }

    public static void print(CommandSpec spec, ResolveResult result, boolean wroteLockfile) {
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        output.summary(
                "Resolved " + result.resolvedCount() + " packages",
                result.downloadCount() + " downloaded",
                result.conflictCount() + " conflicts");
        output.pointer(wroteLockfile ? "wrote" : "verified", result.lockfilePath().toString());
    }
}
