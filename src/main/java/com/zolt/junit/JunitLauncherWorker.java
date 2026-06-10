package com.zolt.junit;

import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class JunitLauncherWorker {
    public static final String MAIN_CLASS = "com.zolt.junit.JunitLauncherWorker";

    public static void main(String[] args) {
        int exitCode = new JunitLauncherWorker().run(args, System.out, System.err);
        System.exit(exitCode);
    }

    int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length != 1 || args[0].isBlank()) {
            err.println("error: JUnit launcher worker requires exactly one test output directory.");
            return 2;
        }
        try {
            return new ProgrammaticLauncher(out).execute(Path.of(args[0]));
        } catch (ReflectiveOperationException | LinkageError exception) {
            err.println("error: Could not run tests through Zolt's JUnit launcher worker. "
                    + "Check that JUnit Platform Launcher and test engines are on the worker classpath.");
            exception.printStackTrace(err);
            return 1;
        }
    }

    private static final class ProgrammaticLauncher {
        private final PrintStream out;

        private ProgrammaticLauncher(PrintStream out) {
            this.out = out;
        }

        private int execute(Path testOutputDirectory) throws ReflectiveOperationException {
            Class<?> listenerClass = Class.forName("org.junit.platform.launcher.listeners.SummaryGeneratingListener");
            Object listener = listenerClass.getDeclaredConstructor().newInstance();
            Object request = discoveryRequest(testOutputDirectory.toAbsolutePath().normalize());
            Object session = Class.forName("org.junit.platform.launcher.core.LauncherFactory")
                    .getMethod("openSession")
                    .invoke(null);
            Class<?> sessionInterface = Class.forName("org.junit.platform.launcher.LauncherSession");
            try {
                Object launcher = sessionInterface.getMethod("getLauncher").invoke(session);
                Class<?> launcherInterface = Class.forName("org.junit.platform.launcher.Launcher");
                Class<?> requestInterface = Class.forName("org.junit.platform.launcher.LauncherDiscoveryRequest");
                Class<?> listenerInterface = Class.forName("org.junit.platform.launcher.TestExecutionListener");
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
            return summarize(listenerClass, listener);
        }

        private static Object discoveryRequest(Path testOutputDirectory) throws ReflectiveOperationException {
            Class<?> selectorsClass = Class.forName("org.junit.platform.engine.discovery.DiscoverySelectors");
            Object selectors = selectorsClass
                    .getMethod("selectClasspathRoots", Set.class)
                    .invoke(null, Set.of(testOutputDirectory));

            Class<?> builderClass = Class.forName("org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder");
            Object builder = builderClass.getMethod("request").invoke(null);
            builderClass.getMethod("selectors", List.class).invoke(builder, selectors);
            return builderClass.getMethod("build").invoke(builder);
        }

        private int summarize(Class<?> listenerClass, Object listener) throws ReflectiveOperationException {
            Object summary = listenerClass.getMethod("getSummary").invoke(listener);
            Class<?> summaryClass = Class.forName("org.junit.platform.launcher.listeners.TestExecutionSummary");
            long found = (long) summaryClass.getMethod("getTestsFoundCount").invoke(summary);
            long succeeded = (long) summaryClass.getMethod("getTestsSucceededCount").invoke(summary);
            long failed = (long) summaryClass.getMethod("getTestsFailedCount").invoke(summary);
            long aborted = (long) summaryClass.getMethod("getTestsAbortedCount").invoke(summary);
            out.println("Tests found: " + found);
            out.println("Tests succeeded: " + succeeded);
            out.println("Tests failed: " + failed);
            if (found == 0) {
                return 2;
            }
            return failed == 0 && aborted == 0 ? 0 : 1;
        }
    }
}
