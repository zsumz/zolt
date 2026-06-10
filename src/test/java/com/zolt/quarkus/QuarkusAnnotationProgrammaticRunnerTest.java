package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import io.quarkus.test.config.ConfigLauncherSession;
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
    void skipsQuarkusDiagnosticWhenRuntimeClassLoaderIsUnavailable() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Object launcher = programmaticLauncher(stream(out));
        Method diagnostic = launcher.getClass()
                .getDeclaredMethod("writeClassLoaderDiagnostic", List.class, ClassLoader.class, Throwable.class);
        diagnostic.setAccessible(true);

        diagnostic.invoke(launcher, List.of(PassingTest.class.getName()), null, new ClassNotFoundException("example.Missing"));

        assertFalse(output(out).contains("Zolt Quarkus classloader diagnostic"));
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

    private static Object programmaticLauncher(PrintStream out) throws Exception {
        Class<?> launcherClass = Class.forName(
                "com.zolt.quarkus.QuarkusAnnotationProgrammaticRunner$ProgrammaticLauncher");
        Constructor<?> constructor = launcherClass.getDeclaredConstructor(PrintStream.class);
        constructor.setAccessible(true);
        return constructor.newInstance(out);
    }

    static final class PassingTest {
        @Test
        void passes() {
        }
    }
}
