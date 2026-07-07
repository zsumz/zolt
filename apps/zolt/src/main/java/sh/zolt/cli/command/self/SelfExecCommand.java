package sh.zolt.cli.command.self;

import sh.zolt.cli.command.CommandFailures;
import sh.zolt.release.update.NativeUpdateException;
import sh.zolt.release.update.NativeVersionExecPlan;
import sh.zolt.release.update.NativeVersionExecRequest;
import sh.zolt.release.update.NativeVersionExecService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "exec", description = "Run a command with an installed native Zolt version.")
public final class SelfExecCommand extends SelfCommand.NativeSelfOptions implements Callable<Integer> {
    private final NativeVersionExecService execService;

    @Parameters(index = "0", paramLabel = "<VERSION>", description = "Installed version to run.")
    private String version;

    @Parameters(index = "1..*", arity = "1..*", paramLabel = "<ARGS>", description = "Command after -- to run.")
    private List<String> arguments;

    @Spec
    private CommandSpec spec;

    public SelfExecCommand() {
        this(new NativeVersionExecService());
    }

    SelfExecCommand(NativeVersionExecService execService) {
        this.execService = execService;
    }

    @Override
    public Integer call() {
        try {
            NativeVersionExecPlan plan = execService.plan(new NativeVersionExecRequest(
                    installRoot(),
                    currentExecutable(),
                    version,
                    arguments));
            return run(plan);
        } catch (IOException | NativeUpdateException exception) {
            throw CommandFailures.user(spec, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw CommandFailures.user(spec, new NativeUpdateException("Native Zolt exec was interrupted.", exception));
        }
    }

    private int run(NativeVersionExecPlan plan) throws IOException, InterruptedException {
        ArrayList<String> command = new ArrayList<>();
        command.add(plan.executable().toString());
        command.addAll(plan.arguments());
        Process process = new ProcessBuilder(command).start();
        Thread stdout = pipe(process.getInputStream(), spec.commandLine().getOut());
        Thread stderr = pipe(process.getErrorStream(), spec.commandLine().getErr());
        int exitCode = process.waitFor();
        stdout.join();
        stderr.join();
        return exitCode;
    }

    private static Thread pipe(InputStream input, PrintWriter output) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    output.println(line);
                    output.flush();
                    line = reader.readLine();
                }
            } catch (IOException exception) {
                // The child process has already exited or closed the stream; keep exec best-effort.
            }
        });
        thread.start();
        return thread;
    }
}
