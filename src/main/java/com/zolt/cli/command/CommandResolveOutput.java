package com.zolt.cli.command;

import com.zolt.resolve.ResolveResult;
import picocli.CommandLine.Model.CommandSpec;

final class CommandResolveOutput {
    private CommandResolveOutput() {
    }

    static void print(CommandSpec spec, ResolveResult result) {
        print(spec, result, true);
    }

    static void print(CommandSpec spec, ResolveResult result, boolean wroteLockfile) {
        spec.commandLine().getOut().println("Resolved " + result.resolvedCount() + " packages");
        spec.commandLine().getOut().println("Downloaded " + result.downloadCount() + " artifacts");
        spec.commandLine().getOut().println("Conflicts " + result.conflictCount());
        if (wroteLockfile) {
            spec.commandLine().getOut().println("Wrote " + result.lockfilePath());
        } else {
            spec.commandLine().getOut().println("Verified " + result.lockfilePath());
        }
    }
}
