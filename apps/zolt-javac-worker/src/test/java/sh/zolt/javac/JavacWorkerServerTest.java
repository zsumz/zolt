package sh.zolt.javac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavacWorkerServerTest {
    @TempDir
    Path tempDir;

    @Test
    void compilesRequestsFromSeparateClientConnections() throws Exception {
        Path state = tempDir.resolve("worker.state");
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        AtomicInteger serverExit = new AtomicInteger(-1);
        CountDownLatch started = new CountDownLatch(1);
        Thread server = Thread.ofPlatform().start(() -> serverExit.set(JavacWorkerServer.run(
                state,
                200,
                new PrintStream(error, true, StandardCharsets.UTF_8),
                started::countDown)));
        assertTrue(started.await(3, TimeUnit.SECONDS), error.toString(StandardCharsets.UTF_8));
        Map<String, String> metadata = metadata(state);

        Path firstSource = source("First");
        Path secondSource = source("Second");
        Path firstOutput = Files.createDirectories(tempDir.resolve("first-classes"));
        Path secondOutput = Files.createDirectories(tempDir.resolve("second-classes"));
        assertSuccessful(request(metadata, List.of(
                "-proc:none", "-d", firstOutput.toString(), firstSource.toString())));
        assertSuccessful(request(metadata, List.of(
                "-proc:none", "-d", secondOutput.toString(), secondSource.toString())));

        server.join(Duration.ofSeconds(3));
        assertEquals(0, serverExit.get(), error.toString(StandardCharsets.UTF_8));
        assertTrue(Files.isRegularFile(firstOutput.resolve("First.class")));
        assertTrue(Files.isRegularFile(secondOutput.resolve("Second.class")));
    }

    private static Response request(Map<String, String> metadata, List<String> arguments) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(),
                    Integer.parseInt(metadata.get("port"))));
            DataOutputStream request = new DataOutputStream(socket.getOutputStream());
            request.writeInt(JavacWorkerServer.MAGIC);
            request.writeInt(JavacWorkerServer.PROTOCOL_VERSION);
            writeString(request, metadata.get("token"));
            request.writeInt(arguments.size());
            for (String argument : arguments) {
                writeString(request, argument);
            }
            request.flush();
            DataInputStream response = new DataInputStream(socket.getInputStream());
            int exitCode = response.readInt();
            int outputLength = response.readInt();
            return new Response(
                    exitCode,
                    new String(response.readNBytes(outputLength), StandardCharsets.UTF_8));
        }
    }

    private static void assertSuccessful(Response response) {
        assertEquals(0, response.exitCode(), response.output());
    }

    private Path source(String name) throws Exception {
        Path source = tempDir.resolve(name + ".java");
        Files.writeString(source, "public class " + name + " {}\n");
        return source;
    }

    private static Map<String, String> metadata(Path state) throws Exception {
        Map<String, String> metadata = new HashMap<>();
        for (String line : Files.readAllLines(state)) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                metadata.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return metadata;
    }

    private static void writeString(DataOutputStream output, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private record Response(int exitCode, String output) {
    }
}
