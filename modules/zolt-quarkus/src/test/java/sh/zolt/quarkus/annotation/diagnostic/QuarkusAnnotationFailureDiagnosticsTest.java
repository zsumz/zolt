package sh.zolt.quarkus.annotation.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.annotation.QuarkusAnnotationProgrammaticRunner;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusAnnotationFailureDiagnosticsTest {
    private final QuarkusAnnotationFailureDiagnostics diagnostics = new QuarkusAnnotationFailureDiagnostics(
            new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

    @Test
    void findsMissingClassFromNestedCausesAndSuppressedFailures() {
        RuntimeException causeWrapper = new RuntimeException(
                "wrapper",
                new NoClassDefFoundError("com/example/Missing"));
        RuntimeException suppressedWrapper = new RuntimeException("suppressed wrapper");
        suppressedWrapper.addSuppressed(new ClassNotFoundException("com.example.Suppressed"));

        assertEquals("com.example.Missing", diagnostics.failingClass(causeWrapper).orElseThrow());
        assertEquals("com.example.Suppressed", diagnostics.failingClass(suppressedWrapper).orElseThrow());
    }

    @Test
    void ignoresFailuresWithoutMissingClassNames() {
        assertTrue(diagnostics.failingClass(null).isEmpty());
        assertTrue(diagnostics.failingClass(new RuntimeException("plain")).isEmpty());
        assertTrue(diagnostics.failingClass(new ClassNotFoundException(" ")).isEmpty());
        assertTrue(diagnostics.failingClass(new NoClassDefFoundError()).isEmpty());
    }

    @Test
    void findsGeneratedInvokerClassFromNestedStackTraces() {
        RuntimeException generatedInvokerFailure = new RuntimeException("generated invoker");
        generatedInvokerFailure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(
                        "com.example.GreetingResource$quarkusrestinvoker$hello_123",
                        "invoke",
                        "GreetingResource.java",
                        12)
        });
        RuntimeException wrapper = new RuntimeException("wrapper", generatedInvokerFailure);

        assertEquals(
                "com.example.GreetingResource$quarkusrestinvoker$hello_123",
                diagnostics.generatedInvokerClass(wrapper).orElseThrow());
        assertTrue(diagnostics.generatedInvokerClass(new RuntimeException("plain")).isEmpty());
    }

    @Test
    void findsGeneratedInvokerClassFromSuppressedFailures() {
        RuntimeException generatedInvokerFailure = new RuntimeException("generated invoker");
        generatedInvokerFailure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(
                        "com.example.HealthResource$quarkusrestinvoker$health_456",
                        "invoke",
                        "HealthResource.java",
                        19)
        });
        RuntimeException wrapper = new RuntimeException("wrapper");
        wrapper.addSuppressed(generatedInvokerFailure);

        assertEquals(
                "com.example.HealthResource$quarkusrestinvoker$health_456",
                diagnostics.generatedInvokerClass(wrapper).orElseThrow());
    }

    @Test
    void skipsClassLoaderDiagnosticWhenRuntimeClassLoaderIsUnavailable() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusAnnotationFailureDiagnostics diagnostics = diagnostics(out);

        diagnostics.writeClassLoaderDiagnostic(
                List.of(DiagnosticFixture.class.getName()),
                null,
                new ClassNotFoundException("example.Missing"));

        assertFalse(output(out).contains("Zolt Quarkus classloader diagnostic"));
    }

    @Test
    void skipsClassLoaderDiagnosticWhenNoTestsWereSelected() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusAnnotationFailureDiagnostics diagnostics = diagnostics(out);

        diagnostics.writeClassLoaderDiagnostic(
                List.of(),
                QuarkusAnnotationFailureDiagnosticsTest.class.getClassLoader(),
                new ClassNotFoundException("example.Missing"));

        assertFalse(output(out).contains("Zolt Quarkus classloader diagnostic"));
    }

    @Test
    void classLoaderDiagnosticReportsGeneratedInvokerVisibilityIndependently() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusAnnotationFailureDiagnostics diagnostics = diagnostics(out);
        NoClassDefFoundError generatedFailure = new NoClassDefFoundError("com/example/MissingResource");
        generatedFailure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(
                        "com.example.MissingResource$quarkusrestinvoker$hello_123",
                        "invoke",
                        null,
                        -1)
        });

        diagnostics.writeClassLoaderDiagnostic(
                List.of(DiagnosticFixture.class.getName()),
                QuarkusAnnotationFailureDiagnosticsTest.class.getClassLoader(),
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
    void classLoaderDiagnosticReportsFirstApplicationClassInStableOrder(@TempDir Path tempDir) throws Exception {
        Path outputDirectory = tempDir.resolve("target/classes");
        Files.createDirectories(outputDirectory.resolve("com/example"));
        Files.write(outputDirectory.resolve("com/example/Zeta.class"), new byte[] {0});
        Files.write(outputDirectory.resolve("com/example/Alpha.class"), new byte[] {0});
        Files.write(outputDirectory.resolve("module-info.class"), new byte[] {0});
        String previous = System.getProperty(QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusAnnotationFailureDiagnostics diagnostics = diagnostics(out);
        try {
            System.setProperty(
                    QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY,
                    outputDirectory.toString());

            diagnostics.writeClassLoaderDiagnostic(
                    List.of(DiagnosticFixture.class.getName()),
                    QuarkusAnnotationFailureDiagnosticsTest.class.getClassLoader(),
                    new ClassNotFoundException("com.example.Missing"));
        } finally {
            restore(QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY, previous);
        }

        String output = output(out);
        assertTrue(output.contains("applicationClass=com.example.Alpha"), output);
        assertTrue(output.contains("application.runtimeClass=<unavailable: ClassNotFoundException>"), output);
    }

    @Test
    void loadedClassLoaderReportsActualLoaderForReachableClasses() {
        Optional<ClassLoader> loader = diagnostics.loadedClassLoader(
                DiagnosticFixture.class.getName(),
                QuarkusAnnotationFailureDiagnosticsTest.class.getClassLoader());

        assertTrue(loader.isPresent());
        assertEquals(DiagnosticFixture.class.getClassLoader(), loader.orElseThrow());
    }

    @Test
    void loadedClassLoaderReturnsEmptyForMissingClasses() {
        assertTrue(diagnostics.loadedClassLoader(
                        "com.example.MissingDiagnosticFixture",
                        QuarkusAnnotationFailureDiagnosticsTest.class.getClassLoader())
                .isEmpty());
    }

    @Test
    void appliesModuleConfigurationThroughRuntimeClassLoaderStartupAction() {
        FakeStartupAction startupAction = new FakeStartupAction();
        FakeQuarkusClassLoader runtimeClassLoader = new FakeQuarkusClassLoader(startupAction);
        ClassLoader targetClassLoader = new ClassLoader(QuarkusAnnotationFailureDiagnosticsTest.class.getClassLoader()) {
        };

        String result = diagnostics.applyModuleConfigurationToClassloader(
                runtimeClassLoader,
                targetClassLoader);

        assertEquals("applied", result);
        assertEquals(targetClassLoader, startupAction.targetClassLoader);
    }

    @Test
    void reportsUnavailableModuleConfigurationWhenStartupActionIsMissing() {
        String result = diagnostics.applyModuleConfigurationToClassloader(
                QuarkusAnnotationFailureDiagnosticsTest.class.getClassLoader(),
                QuarkusAnnotationFailureDiagnosticsTest.class.getClassLoader());

        assertEquals("<unavailable: StartupAction>", result);
    }

    @Test
    void skipsModuleConfigurationWhenClassLoadersAreMissing() {
        assertEquals("skipped", diagnostics.applyModuleConfigurationToClassloader(
                null,
                QuarkusAnnotationFailureDiagnosticsTest.class.getClassLoader()));
        assertEquals("skipped", diagnostics.applyModuleConfigurationToClassloader(
                QuarkusAnnotationFailureDiagnosticsTest.class.getClassLoader(),
                null));
    }

    @Test
    void reportsModuleConfigurationInvocationCause() {
        ThrowingStartupAction startupAction = new ThrowingStartupAction();
        ThrowingQuarkusClassLoader runtimeClassLoader = new ThrowingQuarkusClassLoader(startupAction);

        String result = diagnostics.applyModuleConfigurationToClassloader(
                runtimeClassLoader,
                QuarkusAnnotationFailureDiagnosticsTest.class.getClassLoader());

        assertEquals("<unavailable: IllegalStateException>", result);
    }

    private static QuarkusAnnotationFailureDiagnostics diagnostics(ByteArrayOutputStream out) {
        return new QuarkusAnnotationFailureDiagnostics(new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    private static String output(ByteArrayOutputStream out) {
        return out.toString(StandardCharsets.UTF_8);
    }

    private static void restore(String property, String previous) {
        if (previous == null) {
            System.clearProperty(property);
            return;
        }
        System.setProperty(property, previous);
    }

    static final class DiagnosticFixture {
    }

    static final class FakeQuarkusClassLoader extends ClassLoader {
        private final FakeStartupAction startupAction;

        private FakeQuarkusClassLoader(FakeStartupAction startupAction) {
            super(QuarkusAnnotationFailureDiagnosticsTest.class.getClassLoader());
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

    static final class ThrowingQuarkusClassLoader extends ClassLoader {
        private final ThrowingStartupAction startupAction;

        private ThrowingQuarkusClassLoader(ThrowingStartupAction startupAction) {
            super(QuarkusAnnotationFailureDiagnosticsTest.class.getClassLoader());
            this.startupAction = startupAction;
        }

        public ThrowingStartupAction getStartupAction() {
            return startupAction;
        }
    }

    static final class ThrowingStartupAction {
        public void applyModuleConfigurationToClassloader(ClassLoader classLoader) {
            throw new IllegalStateException("module config failed");
        }
    }

}
