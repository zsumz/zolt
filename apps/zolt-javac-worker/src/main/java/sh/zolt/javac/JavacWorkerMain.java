package sh.zolt.javac;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public final class JavacWorkerMain {
    private static final int MAX_ARGUMENTS = 100_000;
    private static final int MAX_STRING_BYTES = 64 * 1024 * 1024;

    private JavacWorkerMain() {
    }

    public static void main(String[] args) {
        if (args.length == 2 && "--server".equals(args[0])) {
            System.exit(JavacWorkerServer.run(Path.of(args[1]), System.err));
        }
        int exitCode = run(System.in, System.out, System.err);
        System.exit(exitCode);
    }

    static int run(InputStream input, OutputStream output, PrintStream error) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            error.println("error: Zolt javac worker requires a JDK with the system Java compiler.");
            return 2;
        }
        try {
            DataInputStream requests = new DataInputStream(input);
            DataOutputStream responses = new DataOutputStream(output);
            while (true) {
                int argumentCount;
                try {
                    argumentCount = requests.readInt();
                } catch (EOFException exception) {
                    return 0;
                }
                if (argumentCount < 0 || argumentCount > MAX_ARGUMENTS) {
                    error.println("error: Invalid Zolt javac worker argument count: " + argumentCount + ".");
                    return 2;
                }
                List<String> arguments = new ArrayList<>(argumentCount);
                for (int index = 0; index < argumentCount; index++) {
                    arguments.add(readString(requests));
                }
                ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
                int exitCode;
                try {
                    exitCode = compiler.run(
                            null,
                            diagnostics,
                            diagnostics,
                            arguments.toArray(String[]::new));
                } catch (RuntimeException | LinkageError exception) {
                    exitCode = 1;
                    diagnostics.writeBytes(("javac worker failed: " + exception + System.lineSeparator())
                            .getBytes(StandardCharsets.UTF_8));
                }
                responses.writeInt(exitCode);
                writeBytes(responses, diagnostics.toByteArray());
                responses.flush();
            }
        } catch (IOException exception) {
            error.println("error: Zolt javac worker protocol failed: " + exception.getMessage());
            return 1;
        }
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IOException("invalid string length " + length);
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("incomplete string payload");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeBytes(DataOutputStream output, byte[] bytes) throws IOException {
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
