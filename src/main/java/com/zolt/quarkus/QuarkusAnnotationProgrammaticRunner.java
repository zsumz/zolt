package com.zolt.quarkus;

import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class QuarkusAnnotationProgrammaticRunner {
    public static final String MAIN_CLASS = "com.zolt.quarkus.QuarkusAnnotationProgrammaticRunner";
    public static final String TEST_OUTPUT_DIRECTORY_PROPERTY = "zolt.quarkus.test-output-dir";
    private static final String TEST_BUILD_CHAIN_FUNCTION = "io.quarkus.test.junit.TestBuildChainFunction";
    private static final String TEST_BUILD_CHAIN_CUSTOMIZER_SPI =
            "io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer";
    private static final String TEST_BUILD_CHAIN_CUSTOMIZER_SERVICE =
            "META-INF/services/io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer";

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
            exception.printStackTrace(err);
            return 1;
        }
    }

    private static final class ProgrammaticLauncher {
        private final PrintStream out;

        private ProgrammaticLauncher(PrintStream out) {
            this.out = out;
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
            List<Object> selectors = new java.util.ArrayList<>();
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
                    ((Throwable) value).printStackTrace(out);
                    writeClassLoaderDiagnostic(testClasses, quarkusRuntimeClassLoader.get());
                    writeBuildChainCustomizerDiagnostic(quarkusRuntimeClassLoader.get());
                });
            }
        }

        private void writeClassLoaderDiagnostic(List<String> testClasses, ClassLoader quarkusRuntimeClassLoader) {
            if (testClasses.isEmpty() || quarkusRuntimeClassLoader == null) {
                return;
            }
            String testClassName = testClasses.getFirst();
            out.println("Zolt Quarkus classloader diagnostic:");
            out.println("  selectedClass=" + testClassName);
            try {
                Class<?> systemClass = Class.forName(testClassName, false, ClassLoader.getSystemClassLoader());
                out.println("  systemClassLoader=" + classLoaderName(systemClass.getClassLoader()));
                out.println("  quarkusRuntimeClassLoader=" + classLoaderName(quarkusRuntimeClassLoader));
                try {
                    Class<?> runtimeClass = Class.forName(testClassName, false, quarkusRuntimeClassLoader);
                    out.println("  runtimeClassLoader=" + classLoaderName(runtimeClass.getClassLoader()));
                    out.println("  sameClassObject=" + (systemClass == runtimeClass));
                } catch (ReflectiveOperationException | LinkageError exception) {
                    out.println("  runtimeClass=<unavailable: " + exception.getClass().getSimpleName() + ">");
                }
            } catch (ReflectiveOperationException | LinkageError exception) {
                out.println("  systemClass=<unavailable: " + exception.getClass().getSimpleName() + ">");
            }
        }

        private static String classLoaderName(ClassLoader classLoader) {
            if (classLoader == null) {
                return "<bootstrap>";
            }
            return classLoader.getClass().getName() + "@"
                    + Integer.toHexString(System.identityHashCode(classLoader));
        }

        private void writeBuildChainCustomizerDiagnostic(ClassLoader quarkusRuntimeClassLoader) {
            out.println("Zolt Quarkus build-chain diagnostic:");
            writeBuildChainCustomizerDiagnostic("system", ClassLoader.getSystemClassLoader());
            if (quarkusRuntimeClassLoader != null
                    && quarkusRuntimeClassLoader != ClassLoader.getSystemClassLoader()) {
                writeBuildChainCustomizerDiagnostic("quarkusRuntime", quarkusRuntimeClassLoader);
            }
        }

        private void writeBuildChainCustomizerDiagnostic(String label, ClassLoader classLoader) {
            out.println("  " + label + "Loader=" + classLoaderName(classLoader));
            try {
                Class<?> buildChainFunction = Class.forName(TEST_BUILD_CHAIN_FUNCTION, false, classLoader);
                ClassLoader buildChainLoader = buildChainFunction.getClassLoader();
                out.println("    TestBuildChainFunction.loader=" + classLoaderName(buildChainLoader));
                out.println("    serviceResources=" + serviceResources(buildChainLoader));
                out.println("    providers=" + serviceProviders(buildChainLoader));
            } catch (ReflectiveOperationException | LinkageError exception) {
                out.println("    TestBuildChainFunction=<unavailable: "
                        + exception.getClass().getSimpleName()
                        + ">");
            }
        }

        private List<String> serviceResources(ClassLoader classLoader) {
            try {
                Enumeration<URL> resources = classLoader.getResources(TEST_BUILD_CHAIN_CUSTOMIZER_SERVICE);
                List<String> urls = new ArrayList<>();
                while (resources.hasMoreElements()) {
                    urls.add(resources.nextElement().toString());
                }
                return urls.isEmpty() ? List.of("<none>") : List.copyOf(urls);
            } catch (java.io.IOException exception) {
                return List.of("<unavailable: " + exception.getClass().getSimpleName() + ">");
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private List<String> serviceProviders(ClassLoader classLoader) {
            try {
                Class<?> spi = Class.forName(TEST_BUILD_CHAIN_CUSTOMIZER_SPI, false, classLoader);
                ServiceLoader<?> serviceLoader = ServiceLoader.load((Class) spi, classLoader);
                List<String> providers = new ArrayList<>();
                for (Object provider : serviceLoader) {
                    providers.add(provider.getClass().getName()
                            + "@"
                            + classLoaderName(provider.getClass().getClassLoader()));
                }
                return providers.isEmpty() ? List.of("<none>") : List.copyOf(providers);
            } catch (ReflectiveOperationException | LinkageError | ServiceConfigurationError exception) {
                return List.of("<unavailable: " + exception.getClass().getSimpleName() + ">");
            }
        }
    }
}
