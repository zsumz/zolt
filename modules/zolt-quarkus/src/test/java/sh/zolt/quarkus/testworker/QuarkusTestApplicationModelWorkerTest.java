package sh.zolt.quarkus.testworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.bootstrap.QuarkusApplicationModelFactory;
import sh.zolt.quarkus.bootstrap.QuarkusSerializedApplicationModelWriter;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptorReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class QuarkusTestApplicationModelWorkerTest {
    @Test
    void rejectsUnexpectedArgumentCount() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = worker(new ByteArrayOutputStream(), err).run(new String[] {"descriptor"});

        assertEquals(2, exitCode);
        assertTrue(output(err).contains("requires descriptor, output path, and workspace module arguments"));
    }

    @Test
    void reportsDescriptorReadFailures() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = worker(new ByteArrayOutputStream(), err).run(new String[] {
                "/repo/target/quarkus/missing-bootstrap.properties",
                "/repo/target/quarkus/test-application-model.dat",
                "/repo",
                "/repo/target",
                "/repo/src/main/java",
                "/repo/src/main/resources",
                "/repo/target/classes",
                "/repo/src/test/java",
                "/repo/src/test/resources",
                "/repo/target/test-classes"
        });

        assertEquals(1, exitCode);
        assertTrue(output(err).contains("error: Could not read Quarkus bootstrap descriptor"));
        assertTrue(output(err).contains("missing-bootstrap.properties"));
        assertTrue(output(err).contains("cause: java.nio.file.NoSuchFileException"));
    }

    private static QuarkusTestApplicationModelWorker worker(ByteArrayOutputStream out, ByteArrayOutputStream err) {
        return new QuarkusTestApplicationModelWorker(
                new QuarkusBootstrapDescriptorReader(),
                new QuarkusApplicationModelFactory(),
                new QuarkusSerializedApplicationModelWriter(),
                stream(out),
                stream(err));
    }

    private static PrintStream stream(ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }

    private static String output(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }
}
