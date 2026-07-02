package sh.zolt.cli.command;

import sh.zolt.cli.ZoltCli;
import sh.zolt.perf.TimingFormatter;
import sh.zolt.perf.TimingRecorder;
import java.nio.file.Path;
import picocli.CommandLine.Model.CommandSpec;

public final class CommandTimings {
    private CommandTimings() {
    }

    public static TimingRecorder recorder(ZoltCli.TimingOptions options) {
        return new TimingRecorder(options != null && options.enabled());
    }

    public static void print(
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
