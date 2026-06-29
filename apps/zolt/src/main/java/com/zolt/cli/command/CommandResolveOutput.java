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
        output.success("Resolved " + result.resolvedCount() + " packages");
        output.detail("Downloaded " + result.downloadCount() + " artifacts");
        output.detail("Conflicts " + result.conflictCount());
        if (wroteLockfile) {
            output.detail("Wrote " + result.lockfilePath());
        } else {
            output.detail("Verified " + result.lockfilePath());
        }
    }
}
