package sh.zolt.javac;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Executes one worker compile request and writes its response, shared by both transports. The legacy
 * kind keeps the exact {@code compiler.run} behavior; the attributed kind runs the Filer-recording
 * task and appends the attribution section. The response is not flushed here so callers can frame it.
 */
final class WorkerCompile {
    private WorkerCompile() {
    }

    static void run(int kind, List<String> arguments, DataOutputStream response) throws IOException {
        if (kind == WorkerCompileProtocol.KIND_COMPILE_ATTRIBUTED) {
            AttributionCompileResult result = AttributingCompiler.compile(arguments);
            WorkerCompileProtocol.writeResponse(
                    response,
                    result.exitCode(),
                    result.diagnostics().getBytes(StandardCharsets.UTF_8),
                    result);
            return;
        }
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int exitCode;
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            exitCode = compiler.run(null, diagnostics, diagnostics, arguments.toArray(String[]::new));
        } catch (RuntimeException | LinkageError exception) {
            exitCode = 1;
            diagnostics.writeBytes(("javac worker failed: " + exception + System.lineSeparator())
                    .getBytes(StandardCharsets.UTF_8));
        }
        WorkerCompileProtocol.writeResponse(response, exitCode, diagnostics.toByteArray(), null);
    }
}
