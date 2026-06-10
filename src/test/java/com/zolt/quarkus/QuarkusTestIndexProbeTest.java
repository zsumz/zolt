package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class QuarkusTestIndexProbeTest {
    @Test
    void requiresDirectoryAndTestClassArguments() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new QuarkusTestIndexProbe().run(
                new String[] {},
                stream(new ByteArrayOutputStream()),
                stream(err));

        assertEquals(2, exitCode);
        assertTrue(output(err).contains("requires a test output directory and at least one test class"));
    }

    @Test
    void reportsMissingTestOutputDirectory() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new QuarkusTestIndexProbe().run(
                new String[] {"/zolt/no-such-test-output", "com.example.HttpTest"},
                stream(new ByteArrayOutputStream()),
                stream(err));

        assertEquals(2, exitCode);
        assertTrue(output(err).contains("test output directory does not exist"));
        assertTrue(output(err).contains("/zolt/no-such-test-output"));
    }

    @Test
    void formatsStableIndexReport() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusTestIndexProbe.IndexProbeResult result = new QuarkusTestIndexProbe.IndexProbeResult(
                Path.of("/repo/target/test-classes"),
                2,
                List.of(
                        new QuarkusTestIndexProbe.SelectedClassIndexResult(
                                "com.example.HttpTest",
                                true,
                                true,
                                false,
                                List.of("io.quarkus.test.junit.QuarkusTest")),
                        new QuarkusTestIndexProbe.SelectedClassIndexResult(
                                "com.example.MissingTest",
                                false,
                                false,
                                false,
                                List.of())),
                Map.of(
                        "io.quarkus.test.junit.QuarkusTest", 1,
                        "org.junit.jupiter.api.Test", 2));

        QuarkusTestIndexProbe.writeReport(result, stream(out));

        String output = output(out);
        assertTrue(output.contains("Quarkus test index probe"));
        assertTrue(output.contains("Known classes: 2"));
        assertTrue(output.contains("com.example.HttpTest present=true @QuarkusTest=true @ExtendWith=false"));
        assertTrue(output.contains("annotations: io.quarkus.test.junit.QuarkusTest"));
        assertTrue(output.contains("com.example.MissingTest present=false @QuarkusTest=false @ExtendWith=false"));
        assertTrue(output.contains("annotations: <none>"));
        assertTrue(output.contains("io.quarkus.test.junit.QuarkusTest=1"));
    }

    private static PrintStream stream(ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }

    private static String output(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }
}
