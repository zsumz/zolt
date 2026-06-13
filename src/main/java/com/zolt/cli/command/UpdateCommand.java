package com.zolt.cli.command;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "update", description = "Update the Zolt executable in place.")
public final class UpdateCommand implements Callable<Integer> {
    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().getOut().println("""
                zolt update is not available yet.
                Future behavior: check the release channel, download a verified native archive, replace the current zolt executable atomically, and keep a rollback copy.
                Track this work in followUps/-design-zolt-update-command.md.
                """.stripTrailing());
        return 1;
    }
}
