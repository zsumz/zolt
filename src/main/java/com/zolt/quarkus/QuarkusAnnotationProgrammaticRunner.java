package com.zolt.quarkus;

import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class QuarkusAnnotationProgrammaticRunner {
    public static final String MAIN_CLASS = "com.zolt.quarkus.QuarkusAnnotationProgrammaticRunner";

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
            Class<?> listenerInterface = Class.forName("org.junit.platform.launcher.TestExecutionListener");
            AtomicBoolean containsTests = new AtomicBoolean(false);
            AtomicBoolean failed = new AtomicBoolean(false);
            Object listener = listener(listenerInterface, testClasses, containsTests, failed);

            Object request = discoveryRequest(testClasses);
            Object session = Class.forName("org.junit.platform.launcher.core.LauncherFactory")
                    .getMethod("openSession")
                    .invoke(null);
            Class<?> sessionInterface = Class.forName("org.junit.platform.launcher.LauncherSession");
            try {
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

        private Object listener(
                Class<?> listenerInterface,
                List<String> testClasses,
                AtomicBoolean containsTests,
                AtomicBoolean failed) {
            InvocationHandler handler = (proxy, method, args) -> {
                switch (method.getName()) {
                    case "testPlanExecutionStarted" -> {
                        containsTests.set(containsTests(args[0]));
                        switchToQuarkusFacadeTestClassLoader(testClasses);
                    }
                    case "executionFinished" -> recordResult(args[1], failed);
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

        private void switchToQuarkusFacadeTestClassLoader(List<String> testClasses) {
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
                    Thread.currentThread().setContextClassLoader(testClass.getClassLoader());
                }
            } catch (ReflectiveOperationException | LinkageError exception) {
                // Non-Quarkus tests and older Quarkus launchers can run without this handoff.
            }
        }

        private void recordResult(Object result, AtomicBoolean failed) throws ReflectiveOperationException {
            Object status = result.getClass().getMethod("getStatus").invoke(result);
            if ("FAILED".equals(String.valueOf(status))) {
                failed.set(true);
                Optional<?> throwable = (Optional<?>) result.getClass().getMethod("getThrowable").invoke(result);
                throwable.ifPresent(value -> ((Throwable) value).printStackTrace(out));
            }
        }
    }
}
