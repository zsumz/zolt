package com.zolt.junit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class JunitLauncherWorker {
    public static final String MAIN_CLASS = "com.zolt.junit.JunitLauncherWorker";

    public static void main(String[] args) {
        int exitCode = new JunitLauncherWorker().run(args, System.in, System.out, System.err);
        System.exit(exitCode);
    }

    int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, InputStream.nullInputStream(), out, err);
    }

    int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
        if (args != null && args.length == 1 && "--server".equals(args[0])) {
            return runServer(in, out, err);
        }
        if (args == null || args.length != 1 || args[0].isBlank()) {
            err.println("error: JUnit launcher worker requires exactly one test output directory.");
            return 2;
        }
        return runOnce(args[0], out, err);
    }

    private int runServer(InputStream in, PrintStream out, PrintStream err) {
        if (in == null) {
            err.println("error: JUnit launcher worker server requires stdin.");
            return 2;
        }
        ProgrammaticLauncher launcher = new ProgrammaticLauncher(out);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JunitWorkerProtocol.WorkerRequest request = JunitWorkerProtocol.parseRequest(line);
                if (request.command().equals(JunitWorkerProtocol.WorkerCommand.QUIT)) {
                    out.println(JunitWorkerProtocol.result(request.requestId(), 0));
                    out.flush();
                    return 0;
                }
                int exitCode = runRequest(launcher, request.testOutputDirectory(), err);
                out.println(JunitWorkerProtocol.result(request.requestId(), exitCode));
                out.flush();
            }
            return 0;
        } catch (IOException exception) {
            err.println("error: Could not read JUnit launcher worker server input.");
            exception.printStackTrace(err);
            return 1;
        } catch (IllegalArgumentException exception) {
            err.println("error: " + exception.getMessage());
            return 2;
        }
    }

    private int runOnce(String testOutputDirectory, PrintStream out, PrintStream err) {
        return runRequest(new ProgrammaticLauncher(out), testOutputDirectory, err);
    }

    private int runRequest(ProgrammaticLauncher launcher, String testOutputDirectory, PrintStream err) {
        try {
            return launcher.execute(Path.of(testOutputDirectory));
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
            applyDefaultClassNameFilter(builderClass, builder);
            return builderClass.getMethod("build").invoke(builder);
        }

        private static void applyDefaultClassNameFilter(Class<?> builderClass, Object builder)
                throws ReflectiveOperationException {
            Class<?> classNameFilterClass = Class.forName("org.junit.platform.engine.discovery.ClassNameFilter");
            Object standardPattern = classNameFilterClass.getField("STANDARD_INCLUDE_PATTERN").get(null);
            Object classNameFilter = classNameFilterClass
                    .getMethod("includeClassNamePatterns", String[].class)
                    .invoke(null, (Object) new String[] {standardPattern.toString()});

            Class<?> filterInterface = Class.forName("org.junit.platform.engine.Filter");
            Object filters = Array.newInstance(filterInterface, 1);
            Array.set(filters, 0, classNameFilter);
            builderClass
                    .getMethod("filters", filters.getClass())
                    .invoke(builder, filters);
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
