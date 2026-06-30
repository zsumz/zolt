package com.zolt.quarkus.annotation;

import com.zolt.error.WorkerFailureDiagnostic;
import com.zolt.quarkus.annotation.diagnostic.QuarkusAnnotationFailureDiagnostics;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class QuarkusAnnotationProgrammaticRunner {
    public static final String MAIN_CLASS = "com.zolt.quarkus.annotation.QuarkusAnnotationProgrammaticRunner";
    public static final String MAIN_OUTPUT_DIRECTORY_PROPERTY = "zolt.quarkus.main-output-dir";
    public static final String TEST_OUTPUT_DIRECTORY_PROPERTY = "zolt.quarkus.test-output-dir";

    public static void main(String[] args) {
        int exitCode = new QuarkusAnnotationProgrammaticRunner().run(args, System.out, System.err);
        System.exit(exitCode);
    }

    int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length == 0) {
            err.println("error: Quarkus annotation programmatic runner requires at least one test class.");
            return 2;
        }
        try {
            ProgrammaticLauncher launcher = new ProgrammaticLauncher(out);
            return launcher.execute(Arrays.asList(args));
        } catch (ReflectiveOperationException | LinkageError exception) {
            err.println("error: Could not run Quarkus annotation tests through Zolt's programmatic JUnit launcher. "
                    + "Check that JUnit Platform Launcher and Quarkus JUnit are on the test runtime classpath.");
            err.println(WorkerFailureDiagnostic.causeLine(exception));
            return 1;
        }
    }

    private static final class ProgrammaticLauncher {
        private final PrintStream out;
        private final QuarkusAnnotationFailureDiagnostics diagnostics;

        private ProgrammaticLauncher(PrintStream out) {
            this.out = out;
            this.diagnostics = new QuarkusAnnotationFailureDiagnostics(out);
        }

        private int execute(List<String> testClasses) throws ReflectiveOperationException {
            writeQuarkusTestIndex(testClasses);
            Class<?> listenerInterface = Class.forName("org.junit.platform.launcher.TestExecutionListener");
            AtomicBoolean containsTests = new AtomicBoolean(false);
            AtomicBoolean failed = new AtomicBoolean(false);
            AtomicReference<ClassLoader> quarkusRuntimeClassLoader = new AtomicReference<>();
            Object listener = listener(listenerInterface, testClasses, containsTests, failed, quarkusRuntimeClassLoader);

            Object request = discoveryRequest(testClasses);
            Object session = Class.forName("org.junit.platform.launcher.core.LauncherFactory")
                    .getMethod("openSession")
                    .invoke(null);
            Class<?> sessionInterface = Class.forName("org.junit.platform.launcher.LauncherSession");
            try {
                installQuarkusTestConfigSession(session, sessionInterface);
                Object launcher = sessionInterface.getMethod("getLauncher").invoke(session);
                Class<?> launcherInterface = Class.forName("org.junit.platform.launcher.Launcher");
                Class<?> requestInterface = Class.forName("org.junit.platform.launcher.LauncherDiscoveryRequest");
                Method execute = launcherInterface.getMethod(
                        "execute",
                        requestInterface,
                        Array.newInstance(listenerInterface, 0).getClass());
                Object listeners = Array.newInstance(listenerInterface, 1);
                Array.set(listeners, 0, listener);
                execute.invoke(launcher, request, listeners);
            } finally {
                sessionInterface.getMethod("close").invoke(session);
            }

            if (!containsTests.get()) {
                out.println("No tests found");
                return 2;
            }
            if (failed.get()) {
                out.println("Tests failed");
                return 1;
            }
            out.println("Tests passed");
            return 0;
        }

        private void writeQuarkusTestIndex(List<String> testClasses) throws ReflectiveOperationException {
            String testOutputDirectory = System.getProperty(TEST_OUTPUT_DIRECTORY_PROPERTY, "");
            if (testOutputDirectory.isBlank() || testClasses.isEmpty()) {
                return;
            }
            Path outputDirectory = Path.of(testOutputDirectory).toAbsolutePath().normalize();
            new QuarkusTestIndexWriter().write(outputDirectory, testClasses);
        }

        private Object discoveryRequest(List<String> testClasses) throws ReflectiveOperationException {
            Class<?> selectorsClass = Class.forName("org.junit.platform.engine.discovery.DiscoverySelectors");
            Method selectClass = selectorsClass.getMethod("selectClass", String.class);
            List<Object> selectors = new ArrayList<>();
            for (String testClass : testClasses) {
                selectors.add(selectClass.invoke(null, testClass));
            }

            Class<?> builderClass = Class.forName("org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder");
            Object builder = builderClass.getMethod("request").invoke(null);
            builderClass.getMethod("selectors", List.class).invoke(builder, selectors);
            return builderClass.getMethod("build").invoke(builder);
        }

        private void installQuarkusTestConfigSession(Object session, Class<?> sessionInterface) {
            try {
                Class<?> configSessionClass = Class.forName("io.quarkus.test.config.ConfigLauncherSession");
                Object configSession = configSessionClass.getDeclaredConstructor().newInstance();
                configSessionClass
                        .getMethod("launcherSessionOpened", sessionInterface)
                        .invoke(configSession, session);
            } catch (ReflectiveOperationException | LinkageError exception) {
                // Non-Quarkus tests and older Quarkus launchers can run without this setup.
            }
        }

        private Object listener(
                Class<?> listenerInterface,
                List<String> testClasses,
                AtomicBoolean containsTests,
                AtomicBoolean failed,
                AtomicReference<ClassLoader> quarkusRuntimeClassLoader) {
            InvocationHandler handler = (proxy, method, args) -> {
                switch (method.getName()) {
                    case "testPlanExecutionStarted" -> {
                        containsTests.set(containsTests(args[0]));
                        prepareQuarkusConditionEvaluation(testClasses, quarkusRuntimeClassLoader);
                    }
                    case "executionStarted" -> switchToQuarkusRuntimeClassLoader(
                            args[0],
                            testClasses,
                            quarkusRuntimeClassLoader);
                    case "executionFinished" -> recordResult(args[1], failed, testClasses, quarkusRuntimeClassLoader);
                    default -> {
                    }
                }
                return null;
            };
            return Proxy.newProxyInstance(
                    listenerInterface.getClassLoader(),
                    new Class<?>[] {listenerInterface},
                    handler);
        }

        private static boolean containsTests(Object testPlan) throws ReflectiveOperationException {
            return (boolean) testPlan.getClass().getMethod("containsTests").invoke(testPlan);
        }

        private void prepareQuarkusConditionEvaluation(
                List<String> testClasses,
                AtomicReference<ClassLoader> quarkusRuntimeClassLoader) {
            if (testClasses.isEmpty()) {
                return;
            }
            try {
                Class<?> interceptor = Class.forName("io.quarkus.test.junit.launcher.CustomLauncherInterceptor");
                Field facadeLoaderField = interceptor.getDeclaredField("facadeLoader");
                facadeLoaderField.setAccessible(true);
                Object facadeLoader = facadeLoaderField.get(null);
                if (facadeLoader instanceof ClassLoader classLoader) {
                    Class<?> testClass = Class.forName(testClasses.getFirst(), false, classLoader);
                    quarkusRuntimeClassLoader.set(testClass.getClassLoader());
                    Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
                }
            } catch (ReflectiveOperationException | LinkageError exception) {
                // Non-Quarkus tests and older Quarkus launchers can run without this handoff.
            }
        }

        private void switchToQuarkusRuntimeClassLoader(
                Object testIdentifier,
                List<String> testClasses,
                AtomicReference<ClassLoader> quarkusRuntimeClassLoader) throws ReflectiveOperationException {
            ClassLoader classLoader = quarkusRuntimeClassLoader.get();
            if (classLoader != null && selectedTestClassStarted(testIdentifier, testClasses)) {
                Thread.currentThread().setContextClassLoader(classLoader);
            }
        }

        private static boolean selectedTestClassStarted(Object testIdentifier, List<String> testClasses)
                throws ReflectiveOperationException {
            return sourceClassName(testIdentifier)
                    .map(testClasses::contains)
                    .orElse(false);
        }

        private static Optional<String> sourceClassName(Object testIdentifier)
                throws ReflectiveOperationException {
            Optional<?> source = (Optional<?>) testIdentifier.getClass().getMethod("getSource").invoke(testIdentifier);
            if (source.isEmpty()) {
                return Optional.empty();
            }
            Object testSource = source.get();
            try {
                Object className = testSource.getClass().getMethod("getClassName").invoke(testSource);
                return Optional.of(String.valueOf(className));
            } catch (NoSuchMethodException exception) {
                return Optional.empty();
            }
        }

        private void recordResult(
                Object result,
                AtomicBoolean failed,
                List<String> testClasses,
                AtomicReference<ClassLoader> quarkusRuntimeClassLoader) throws ReflectiveOperationException {
            Object status = result.getClass().getMethod("getStatus").invoke(result);
            if ("FAILED".equals(String.valueOf(status))) {
                failed.set(true);
                Optional<?> throwable = (Optional<?>) result.getClass().getMethod("getThrowable").invoke(result);
                throwable.ifPresent(value -> {
                    // Per-test failure report to stdout: the parent test runner scans this trace for
                    // hidden bootstrap failures (see QuarkusFrameworkTestRunner.failOnHiddenBootstrapFailure),
                    // so it is a deliberate, allowlisted stdout report rather than an error diagnostic.
                    ((Throwable) value).printStackTrace(out);
                    diagnostics.writeFailure(testClasses, quarkusRuntimeClassLoader.get(), (Throwable) value);
                });
            }
        }

    }
}
