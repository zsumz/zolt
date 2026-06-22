package com.zolt.cli.command;

import com.zolt.cli.ZoltCli;
import com.zolt.perf.TimingFormatter;
import com.zolt.perf.TimingRecorder;
import java.nio.file.Path;
import picocli.CommandLine.Model.CommandSpec;

final class CommandTimings {
    private CommandTimings() {
    }

    static TimingRecorder recorder(ZoltCli.TimingOptions options) {
        return new TimingRecorder(options != null && options.enabled());
    }

    static void print(
            CommandSpec spec,
            String command,
            Path projectRoot,
            ZoltCli.TimingOptions options,
            TimingRecorder recorder) {
        if (options == null || !options.enabled() || recorder.events().isEmpty()) {
            return;
        }
        spec.commandLine().getErr().print(TimingFormatter.format(
                options.format(),
                command,
                projectRoot,
                recorder.events()));
        spec.commandLine().getErr().flush();
    }
}
