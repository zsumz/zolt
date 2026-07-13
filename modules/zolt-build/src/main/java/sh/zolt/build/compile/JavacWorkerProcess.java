package sh.zolt.build.compile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class JavacWorkerProcess implements AutoCloseable {
    private static final String MAIN_CLASS = "sh.zolt.javac.JavacWorkerMain";
    private static final int MAX_RESPONSE_BYTES = 64 * 1024 * 1024;
    private static final int CLOSE_TIMEOUT_SECONDS = 2;

    private final Process process;
    private final DataInputStream responses;
    private final DataOutputStream requests;

    private JavacWorkerProcess(Process process) {
        this.process = process;
        this.responses = new DataInputStream(process.getInputStream());
        this.requests = new DataOutputStream(process.getOutputStream());
    }

    static JavacWorkerProcess start(Path javac, Path workerJar) throws IOException {
        Path java = javac.toAbsolutePath().normalize().resolveSibling(javaExecutableName(javac));
        Process process = new ProcessBuilder(
                java.toString(),
                "-classpath",
                workerJar.toAbsolutePath().normalize().toString(),
                MAIN_CLASS)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        return new JavacWorkerProcess(process);
    }

    JavacRunner.ProcessResult compile(List<String> arguments) throws IOException {
        requests.writeInt(arguments.size());
        for (String argument : arguments) {
            byte[] bytes = argument.getBytes(StandardCharsets.UTF_8);
            requests.writeInt(bytes.length);
            requests.write(bytes);
        }
        requests.flush();
        int exitCode = responses.readInt();
        int outputLength = responses.readInt();
        if (outputLength < 0 || outputLength > MAX_RESPONSE_BYTES) {
            throw new IOException("invalid javac worker response length " + outputLength);
        }
        byte[] outputBytes = responses.readNBytes(outputLength);
        if (outputBytes.length != outputLength) {
            throw new IOException("incomplete javac worker response");
        }
        String output = new String(outputBytes, StandardCharsets.UTF_8);
        return new JavacRunner.ProcessResult(exitCode, output);
    }

    boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public void close() {
        try {
            requests.close();
            if (!process.waitFor(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (IOException ignored) {
            process.destroyForcibly();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static String javaExecutableName(Path javac) {
        String fileName = javac.getFileName().toString();
        return fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".exe") ? "java.exe" : "java";
    }
}
