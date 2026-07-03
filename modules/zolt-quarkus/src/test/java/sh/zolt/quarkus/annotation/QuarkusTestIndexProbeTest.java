package sh.zolt.quarkus.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.testsupport.QuarkusJandexFixtureCompiler;
import sh.zolt.quarkus.testsupport.QuarkusJandexFixtureCompiler.FixtureRuntime;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
                List.of(new QuarkusTestIndexProbe.TestClassLocationResult(
                        "com.example.HttpTest",
                        "/repo/target/test-classes")),
                new QuarkusTestIndexProbe.IndexProbeSnapshot(
                        "Raw",
                        2,
                        List.of(
                                new QuarkusTestIndexProbe.SelectedClassIndexResult(
                                        "com.example.HttpTest",
                                        true,
                                        true,
                                        false,
                                        List.of("io.quarkus.test.junit.QuarkusTest"))),
                        Map.of(
                                "io.quarkus.test.junit.QuarkusTest", 1,
                                "org.junit.jupiter.api.Test", 2),
                        List.of()),
                new QuarkusTestIndexProbe.IndexProbeSnapshot(
                        "Enriched",
                        2,
                        List.of(
                                new QuarkusTestIndexProbe.SelectedClassIndexResult(
                                        "com.example.HttpTest",
                                        true,
                                        true,
                                        true,
                                        List.of("io.quarkus.test.junit.QuarkusTest")),
                                new QuarkusTestIndexProbe.SelectedClassIndexResult(
                                        "com.example.MissingTest",
                                        false,
                                        false,
                                        false,
                                        List.of())),
                        Map.of(
                                "io.quarkus.test.junit.QuarkusTest", 1,
                                "org.junit.jupiter.api.Test", 2,
                                "org.junit.jupiter.api.extension.ExtendWith", 1),
                        List.of("com.example.HttpTest")));

        QuarkusTestIndexProbe.writeReport(result, stream(out));

        String output = output(out);
        assertTrue(output.contains("Quarkus test index probe"));
        assertTrue(output.contains("Class locations:"));
        assertTrue(output.contains("com.example.HttpTest location=/repo/target/test-classes"));
        assertTrue(output.contains("Raw index:"));
        assertTrue(output.contains("Enriched index:"));
        assertTrue(output.contains("Known classes: 2"));
        assertTrue(output.contains("com.example.HttpTest present=true @QuarkusTest=true @ExtendWith=false"));
        assertTrue(output.contains("com.example.HttpTest present=true @QuarkusTest=true @ExtendWith=true"));
        assertTrue(output.contains("annotations: io.quarkus.test.junit.QuarkusTest"));
        assertTrue(output.contains("com.example.MissingTest present=false @QuarkusTest=false @ExtendWith=false"));
        assertTrue(output.contains("annotations: <none>"));
        assertTrue(output.contains("io.quarkus.test.junit.QuarkusTest=1"));
        assertTrue(output.contains("org.junit.jupiter.api.extension.ExtendWith=1"));
        assertTrue(output.contains("Build-chain test bean candidates:"));
        assertTrue(output.contains("<none>"));
        assertTrue(output.contains("com.example.HttpTest"));
    }

    @Test
    void probesRealIndexAndReportsSyntheticQuarkusTestExtensionCandidate(@TempDir Path tempDir)
            throws Exception {
        Path testOutputDirectory = QuarkusJandexFixtureCompiler.compile(
                tempDir.resolve("test-classes"),
                Map.of("com.example.HttpQuarkusCase", """
                        package com.example;

                        @io.quarkus.test.junit.QuarkusTest
                        public class HttpQuarkusCase {
                        }
                        """));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode;
        try (FixtureRuntime runtime = QuarkusJandexFixtureCompiler.fixtureRuntime()) {
            Object probe = runtime.newInstance(QuarkusTestIndexProbe.MAIN_CLASS);
            Method run = probe.getClass().getDeclaredMethod(
                    "run",
                    String[].class,
                    PrintStream.class,
                    PrintStream.class);
            run.setAccessible(true);
            exitCode = (int) run.invoke(
                    probe,
                    new String[] {
                            testOutputDirectory.toString(),
                            "com.example.HttpQuarkusCase",
                            "com.example.MissingCase"
                    },
                    stream(out),
                    stream(err));
        }

        assertEquals(0, exitCode, output(err));
        String output = output(out);
        assertTrue(output.contains("com.example.HttpQuarkusCase location=<unavailable: ClassNotFoundException>"));
        assertTrue(output.contains("Raw index:"));
        assertTrue(output.contains("com.example.HttpQuarkusCase present=true @QuarkusTest=true @ExtendWith=false"));
        assertTrue(output.contains("Enriched index:"));
        assertTrue(output.contains("com.example.HttpQuarkusCase present=true @QuarkusTest=true @ExtendWith=true"));
        assertTrue(output.contains("com.example.MissingCase present=false @QuarkusTest=false @ExtendWith=false"));
        assertTrue(output.contains("org.junit.jupiter.api.extension.ExtendWith=0"));
        assertTrue(output.contains("org.junit.jupiter.api.extension.ExtendWith=1"));
        assertTrue(output.contains("annotations: io.quarkus.test.junit.QuarkusTest"));
        assertTrue(output.contains("    <none>"));
        assertTrue(output.contains("    com.example.HttpQuarkusCase"));
    }

    @Test
    void probesRegisterExtensionCandidatesFromRealIndex(@TempDir Path tempDir) throws Exception {
        Path testOutputDirectory = QuarkusJandexFixtureCompiler.compile(
                tempDir.resolve("test-classes"),
                Map.of("com.example.RegisteredExtensionCase", """
                        package com.example;

                        public class RegisteredExtensionCase {
                            @org.junit.jupiter.api.extension.RegisterExtension
                            static io.quarkus.test.junit.QuarkusTestExtension extension;

                            @org.junit.jupiter.api.Test
                            void runs() {
                            }
                        }
                        """));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode;
        try (FixtureRuntime runtime = QuarkusJandexFixtureCompiler.fixtureRuntime()) {
            Object probe = runtime.newInstance(QuarkusTestIndexProbe.MAIN_CLASS);
            Method run = probe.getClass().getDeclaredMethod(
                    "run",
                    String[].class,
                    PrintStream.class,
                    PrintStream.class);
            run.setAccessible(true);
            exitCode = (int) run.invoke(
                    probe,
                    new String[] {
                            testOutputDirectory.toString(),
                            "com.example.RegisteredExtensionCase"
                    },
                    stream(out),
                    stream(err));
        }

        assertEquals(0, exitCode, output(err));
        String output = output(out);
        assertTrue(output.contains(
                "com.example.RegisteredExtensionCase present=true @QuarkusTest=false @ExtendWith=false"));
        assertTrue(output.contains("org.junit.jupiter.api.extension.RegisterExtension=1"));
        assertTrue(output.contains("org.junit.jupiter.api.Test=1"));
        assertTrue(output.contains("Build-chain test bean candidates:"));
        assertTrue(output.contains("    com.example.RegisteredExtensionCase"));
    }

    private static PrintStream stream(ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }

    private static String output(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }
}
