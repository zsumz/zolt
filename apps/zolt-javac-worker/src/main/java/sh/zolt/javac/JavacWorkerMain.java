package sh.zolt.javac;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;

public final class JavacWorkerMain {
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
        if (ToolProvider.getSystemJavaCompiler() == null) {
            error.println("error: Zolt javac worker requires a JDK with the system Java compiler.");
            return 2;
        }
        try {
            DataInputStream requests = new DataInputStream(input);
            DataOutputStream responses = new DataOutputStream(output);
            while (true) {
                int kind;
                try {
                    kind = requests.readInt();
                } catch (EOFException exception) {
                    return 0;
                }
                if (kind != WorkerCompileProtocol.KIND_COMPILE
                        && kind != WorkerCompileProtocol.KIND_COMPILE_ATTRIBUTED) {
                    error.println("error: Invalid Zolt javac worker request kind: " + kind + ".");
                    return 2;
                }
                List<String> arguments = WorkerCompileProtocol.readArguments(requests);
                WorkerCompile.run(kind, arguments, responses);
                responses.flush();
            }
        } catch (IOException exception) {
            error.println("error: Zolt javac worker protocol failed: " + exception.getMessage());
            return 1;
        }
    }
}
