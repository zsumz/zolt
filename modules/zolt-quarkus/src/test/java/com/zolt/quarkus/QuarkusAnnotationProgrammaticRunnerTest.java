package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.config.ConfigLauncherSession;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class QuarkusAnnotationProgrammaticRunnerTest {
    @Test
    void executesSelectedJunitClassReflectively() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ConfigLauncherSession.reset();

        int exitCode = new QuarkusAnnotationProgrammaticRunner().run(
                new String[] {PassingTest.class.getName()},
                stream(out),
                stream(err));

        assertEquals(0, exitCode);
        assertTrue(output(out).contains("Tests passed"));
        assertEquals("", output(err));
        assertTrue(ConfigLauncherSession.opened());
    }

    @Test
    void returnsNoTestsStatusForClassesWithoutTests() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new QuarkusAnnotationProgrammaticRunner().run(
                new String[] {String.class.getName()},
                stream(out),
                stream(err));

        assertEquals(2, exitCode);
        assertTrue(output(out).contains("No tests found"));
        assertEquals("", output(err));
    }

    @Test
    void requiresTestClassArguments() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = new QuarkusAnnotationProgrammaticRunner().run(
                new String[] {},
                stream(new ByteArrayOutputStream()),
                stream(err));

        assertEquals(2, exitCode);
        assertTrue(output(err).contains("requires at least one test class"));
    }

    private static PrintStream stream(ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }

    private static String output(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
    }

    static final class PassingTest {
        @Test
        void passes() {
        }
    }
}
