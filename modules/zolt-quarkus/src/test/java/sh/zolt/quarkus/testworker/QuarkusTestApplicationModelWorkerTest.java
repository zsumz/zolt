package sh.zolt.quarkus.testworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.bootstrap.QuarkusApplicationModelFactory;
import sh.zolt.quarkus.bootstrap.QuarkusSerializedApplicationModelWriter;
import sh.zolt.quarkus.bootstrap.QuarkusWorkspaceModuleInputs;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptorReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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

    @Test
    void mapsWorkspaceModuleArgumentsInOrder() throws Exception {
        Method method = QuarkusTestApplicationModelWorker.class.getDeclaredMethod(
                "workspaceModuleInputs",
                String[].class);
        method.setAccessible(true);

        QuarkusWorkspaceModuleInputs inputs = (QuarkusWorkspaceModuleInputs) method.invoke(null, (Object) new String[] {
                "/repo/target/quarkus/zolt-bootstrap.properties",
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

        assertEquals(Path.of("/repo"), inputs.projectDirectory());
        assertEquals(Path.of("/repo/target"), inputs.buildDirectory());
        assertEquals(Path.of("/repo/src/main/java"), inputs.mainSourceDirectory());
        assertEquals(Path.of("/repo/src/main/resources"), inputs.mainResourceDirectory());
        assertEquals(Path.of("/repo/target/classes"), inputs.mainOutputDirectory());
        assertEquals(Path.of("/repo/src/test/java"), inputs.testSourceDirectory());
        assertEquals(Path.of("/repo/src/test/resources"), inputs.testResourceDirectory());
        assertEquals(Path.of("/repo/target/test-classes"), inputs.testOutputDirectory());
    }

    @Test
    void validatesWorkerConstructionInputs() {
        assertTrue(assertThrows(
                        QuarkusAugmentationException.class,
                        () -> new QuarkusTestApplicationModelWorker(
                                null,
                                new QuarkusApplicationModelFactory(),
                                new QuarkusSerializedApplicationModelWriter(),
                                stream(new ByteArrayOutputStream()),
                                stream(new ByteArrayOutputStream())))
                .getMessage()
                .contains("descriptor reader is required"));
        assertTrue(assertThrows(
                        QuarkusAugmentationException.class,
                        () -> new QuarkusTestApplicationModelWorker(
                                new QuarkusBootstrapDescriptorReader(),
                                null,
                                new QuarkusSerializedApplicationModelWriter(),
                                stream(new ByteArrayOutputStream()),
                                stream(new ByteArrayOutputStream())))
                .getMessage()
                .contains("application model factory is required"));
        assertTrue(assertThrows(
                        QuarkusAugmentationException.class,
                        () -> new QuarkusTestApplicationModelWorker(
                                new QuarkusBootstrapDescriptorReader(),
                                new QuarkusApplicationModelFactory(),
                                null,
                                stream(new ByteArrayOutputStream()),
                                stream(new ByteArrayOutputStream())))
                .getMessage()
                .contains("model writer is required"));
        assertTrue(assertThrows(
                        QuarkusAugmentationException.class,
                        () -> new QuarkusTestApplicationModelWorker(
                                new QuarkusBootstrapDescriptorReader(),
                                new QuarkusApplicationModelFactory(),
                                new QuarkusSerializedApplicationModelWriter(),
                                null,
                                stream(new ByteArrayOutputStream())))
                .getMessage()
                .contains("output stream is required"));
        assertTrue(assertThrows(
                        QuarkusAugmentationException.class,
                        () -> new QuarkusTestApplicationModelWorker(
                                new QuarkusBootstrapDescriptorReader(),
                                new QuarkusApplicationModelFactory(),
                                new QuarkusSerializedApplicationModelWriter(),
                                stream(new ByteArrayOutputStream()),
                                null))
                .getMessage()
                .contains("error stream is required"));
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
