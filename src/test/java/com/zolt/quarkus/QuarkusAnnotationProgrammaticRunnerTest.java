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
import java.util.Optional;
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
    void diagnosticReportsGeneratedInvokerVisibilityIndependently() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Object launcher = programmaticLauncher(stream(out));
        Method diagnostic = launcher.getClass()
                .getDeclaredMethod("writeClassLoaderDiagnostic", List.class, ClassLoader.class, Throwable.class);
        diagnostic.setAccessible(true);
        NoClassDefFoundError generatedFailure = new NoClassDefFoundError("com/example/MissingResource");
        generatedFailure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(
                        "com.example.MissingResource$quarkusrestinvoker$hello_123",
                        "invoke",
                        null,
                        -1)
        });

        diagnostic.invoke(
                launcher,
                List.of(PassingTest.class.getName()),
                QuarkusAnnotationProgrammaticRunnerTest.class.getClassLoader(),
                generatedFailure);

        String output = output(out);
        assertTrue(output.contains("Zolt Quarkus classloader diagnostic"));
        assertTrue(output.contains("generatedInvokerClass=com.example.MissingResource$quarkusrestinvoker$hello_123"));
        assertTrue(output.contains("generatedInvoker.systemClass=<unavailable: ClassNotFoundException>"));
        assertTrue(output.contains("generatedInvoker.runtimeClass=<unavailable: ClassNotFoundException>"));
        assertTrue(output.contains("failingClass=com.example.MissingResource"));
        assertTrue(output.contains("failing.runtimeClass=<unavailable: ClassNotFoundException>"));
    }

    @Test
    void loadedClassLoaderReportsActualLoaderForReachableClasses() throws Exception {
        Object launcher = programmaticLauncher(stream(new ByteArrayOutputStream()));
        Method loadedClassLoader = launcher.getClass()
                .getDeclaredMethod("loadedClassLoader", String.class, ClassLoader.class);
        loadedClassLoader.setAccessible(true);

        Optional<?> loader = (Optional<?>) loadedClassLoader.invoke(
                launcher,
                PassingTest.class.getName(),
                QuarkusAnnotationProgrammaticRunnerTest.class.getClassLoader());

        assertTrue(loader.isPresent());
        assertEquals(PassingTest.class.getClassLoader(), loader.orElseThrow());
    }

    @Test
    void appliesModuleConfigurationThroughRuntimeClassLoaderStartupAction() throws Exception {
        Object launcher = programmaticLauncher(stream(new ByteArrayOutputStream()));
        Method applyModuleConfiguration = launcher.getClass()
                .getDeclaredMethod("applyModuleConfigurationToClassloader", ClassLoader.class, ClassLoader.class);
        applyModuleConfiguration.setAccessible(true);
        FakeStartupAction startupAction = new FakeStartupAction();
        FakeQuarkusClassLoader runtimeClassLoader = new FakeQuarkusClassLoader(startupAction);
        ClassLoader targetClassLoader = new ClassLoader(QuarkusAnnotationProgrammaticRunnerTest.class.getClassLoader()) {
        };

        String result = String.valueOf(applyModuleConfiguration.invoke(
                launcher,
                runtimeClassLoader,
                targetClassLoader));

        assertEquals("applied", result);
        assertEquals(targetClassLoader, startupAction.targetClassLoader);
    }

    @Test
    void reportsUnavailableModuleConfigurationWhenStartupActionIsMissing() throws Exception {
        Object launcher = programmaticLauncher(stream(new ByteArrayOutputStream()));
        Method applyModuleConfiguration = launcher.getClass()
                .getDeclaredMethod("applyModuleConfigurationToClassloader", ClassLoader.class, ClassLoader.class);
        applyModuleConfiguration.setAccessible(true);

        String result = String.valueOf(applyModuleConfiguration.invoke(
                launcher,
                QuarkusAnnotationProgrammaticRunnerTest.class.getClassLoader(),
                QuarkusAnnotationProgrammaticRunnerTest.class.getClassLoader()));

        assertEquals("<unavailable: StartupAction>", result);
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

    static final class FakeQuarkusClassLoader extends ClassLoader {
        private final FakeStartupAction startupAction;

        private FakeQuarkusClassLoader(FakeStartupAction startupAction) {
            super(QuarkusAnnotationProgrammaticRunnerTest.class.getClassLoader());
            this.startupAction = startupAction;
        }

        public FakeStartupAction getStartupAction() {
            return startupAction;
        }
    }

    static final class FakeStartupAction {
        private ClassLoader targetClassLoader;

        public void applyModuleConfigurationToClassloader(ClassLoader classLoader) {
            targetClassLoader = classLoader;
        }
    }
}
