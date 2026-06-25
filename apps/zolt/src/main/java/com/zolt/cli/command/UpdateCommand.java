package com.zolt.cli.command;

import com.zolt.cli.CommandHumanOutput;
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
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        output.work("zolt update is not available yet.");
        output.line("Future behavior: check the release channel, download a verified native archive, "
                + "replace the current zolt executable atomically, and keep a rollback copy.");
        output.next("Track this work in followUps/-design-zolt-update-command.md.");
        return 1;
    }
}
