package sh.zolt.javac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavacWorkerMainTest {
    @TempDir
    Path tempDir;

    @Test
    void compilesMultipleRequestsInOneWorkerSession() throws Exception {
        Path firstSource = writeSource("First");
        Path secondSource = writeSource("Second");
        Path firstOutput = tempDir.resolve("first-classes");
        Path secondOutput = tempDir.resolve("second-classes");
        Files.createDirectories(firstOutput);
        Files.createDirectories(secondOutput);
        ByteArrayOutputStream inputBytes = new ByteArrayOutputStream();
        try (DataOutputStream input = new DataOutputStream(inputBytes)) {
            writeRequest(input, List.of("-proc:none", "-d", firstOutput.toString(), firstSource.toString()));
            writeRequest(input, List.of("-proc:none", "-d", secondOutput.toString(), secondSource.toString()));
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int exitCode = JavacWorkerMain.run(
                new ByteArrayInputStream(inputBytes.toByteArray()),
                output,
                new PrintStream(error, true, StandardCharsets.UTF_8));

        assertEquals(0, exitCode, error.toString(StandardCharsets.UTF_8));
        try (DataInputStream responses = new DataInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            assertSuccessfulResponse(responses);
            assertSuccessfulResponse(responses);
        }
        assertTrue(Files.isRegularFile(firstOutput.resolve("First.class")));
        assertTrue(Files.isRegularFile(secondOutput.resolve("Second.class")));
    }

    private Path writeSource(String name) throws Exception {
        Path source = tempDir.resolve(name + ".java");
        Files.writeString(source, "public class " + name + " {}\n");
        return source;
    }

    private static void writeRequest(DataOutputStream output, List<String> arguments) throws Exception {
        output.writeInt(arguments.size());
        for (String argument : arguments) {
            byte[] bytes = argument.getBytes(StandardCharsets.UTF_8);
            output.writeInt(bytes.length);
            output.write(bytes);
        }
    }

    private static void assertSuccessfulResponse(DataInputStream input) throws Exception {
        assertEquals(0, input.readInt());
        int outputLength = input.readInt();
        String output = new String(input.readNBytes(outputLength), StandardCharsets.UTF_8);
        assertTrue(output.isBlank(), output);
    }
}
