package com.zolt.junit;

import com.zolt.error.WorkerFailureDiagnostic;
import com.zolt.test.TestSelection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
                int exitCode = runRequest(
                        launcher,
                        request.testOutputDirectory(),
                        request.testSelection(),
                        request.reportsDirectory(),
                        request.profileDirectory(),
                        request.events(),
                        err);
                out.println(JunitWorkerProtocol.result(request.requestId(), exitCode));
                out.flush();
            }
            return 0;
        } catch (IOException exception) {
            err.println("error: Could not read JUnit launcher worker server input.");
            err.println(WorkerFailureDiagnostic.causeLine(exception));
            return 1;
        } catch (IllegalArgumentException exception) {
            err.println("error: " + exception.getMessage());
            return 2;
        }
    }

    private int runOnce(String testOutputDirectory, PrintStream out, PrintStream err) {
        return runRequest(
                new ProgrammaticLauncher(out),
                testOutputDirectory,
                TestSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                err);
    }

    private int runRequest(
            ProgrammaticLauncher launcher,
            String testOutputDirectory,
            TestSelection testSelection,
            Optional<String> reportsDirectory,
            Optional<String> profileDirectory,
            List<String> events,
            PrintStream err) {
        try {
            return launcher.execute(
                    Path.of(testOutputDirectory),
                    testSelection,
                    reportsDirectory.map(Path::of),
                    profileDirectory.map(Path::of),
                    events);
        } catch (ReflectiveOperationException | IOException | LinkageError exception) {
            err.println("error: Could not run tests through Zolt's JUnit launcher worker. "
                    + "Check that JUnit Platform Launcher and test engines are on the worker classpath.");
            err.println(WorkerFailureDiagnostic.causeLine(exception));
            return 1;
        }
    }

    private static final class ProgrammaticLauncher {
        private final PrintStream out;

        private ProgrammaticLauncher(PrintStream out) {
            this.out = out;
        }

        private int execute(
                Path testOutputDirectory,
                TestSelection testSelection,
                Optional<Path> reportsDirectory,
                Optional<Path> profileDirectory,
                List<String> events) throws ReflectiveOperationException, IOException {
            Class<?> listenerClass = Class.forName("org.junit.platform.launcher.listeners.SummaryGeneratingListener");
            Object listener = listenerClass.getDeclaredConstructor().newInstance();
            Object request = discoveryRequest(testOutputDirectory.toAbsolutePath().normalize(), testSelection);
            Object session = openSession();
            Class<?> sessionInterface = Class.forName("org.junit.platform.launcher.LauncherSession");
            JunitTestProfileCollector profileCollector = profileDirectory == null || profileDirectory.isEmpty()
                    ? null
                    : new JunitTestProfileCollector(profileDirectory.orElseThrow());
            try {
                Object launcher = sessionInterface.getMethod("getLauncher").invoke(session);
                Class<?> launcherInterface = Class.forName("org.junit.platform.launcher.Launcher");
                Class<?> requestInterface = Class.forName("org.junit.platform.launcher.LauncherDiscoveryRequest");
                Class<?> listenerInterface = Class.forName("org.junit.platform.launcher.TestExecutionListener");
                List<Object> testExecutionListeners = new ArrayList<>();
                testExecutionListeners.add(listener);
                if (reportsDirectory != null && reportsDirectory.isPresent()) {
                    testExecutionListeners.add(reportListener(reportsDirectory.orElseThrow()));
                }
                if (profileCollector != null) {
                    testExecutionListeners.add(profileCollector.listener(listenerInterface));
                }
                Method execute = launcherInterface.getMethod(
                        "execute",
                        requestInterface,
                        Array.newInstance(listenerInterface, 0).getClass());
                Object listeners = Array.newInstance(listenerInterface, testExecutionListeners.size());
                for (int index = 0; index < testExecutionListeners.size(); index++) {
                    Array.set(listeners, index, testExecutionListeners.get(index));
                }
                execute.invoke(launcher, request, listeners);
            } finally {
                try {
                    sessionInterface.getMethod("close").invoke(session);
                } finally {
                    if (profileCollector != null) {
                        profileCollector.write();
                    }
                }
            }
            return summarize(listenerClass, listener);
        }

        private Object openSession() throws ReflectiveOperationException {
            Class<?> launcherFactoryClass = Class.forName("org.junit.platform.launcher.core.LauncherFactory");
            try {
                Class<?> launcherConfigClass = Class.forName("org.junit.platform.launcher.core.LauncherConfig");
                Object builder = launcherConfigClass.getMethod("builder").invoke(null);
                // Keep plain JUnit worker runs isolated from framework launcher listeners on Zolt's own classpath.
                disableAutoRegistration(builder, "enableLauncherSessionListenerAutoRegistration");
                disableAutoRegistration(builder, "enableLauncherDiscoveryListenerAutoRegistration");
                disableAutoRegistration(builder, "enableTestExecutionListenerAutoRegistration");
                disableAutoRegistration(builder, "enablePostDiscoveryFilterAutoRegistration");
                Method build = builder.getClass().getMethod("build");
                build.setAccessible(true);
                Object launcherConfig = build.invoke(builder);
                return launcherFactoryClass
                        .getMethod("openSession", launcherConfigClass)
                        .invoke(null, launcherConfig);
            } catch (ClassNotFoundException | NoSuchMethodException exception) {
                return launcherFactoryClass.getMethod("openSession").invoke(null);
            }
        }

        private static void disableAutoRegistration(Object builder, String methodName)
                throws ReflectiveOperationException {
            Method method = builder.getClass().getMethod(methodName, boolean.class);
            method.setAccessible(true);
            method.invoke(builder, false);
        }

        private Object reportListener(Path reportsDirectory) throws ReflectiveOperationException, IOException {
            Path normalizedDirectory = reportsDirectory.toAbsolutePath().normalize();
            Files.createDirectories(normalizedDirectory);
            Class<?> reportListenerClass = Class.forName(
                    "org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener");
            return reportListenerClass
                    .getConstructor(Path.class, PrintWriter.class)
                    .newInstance(normalizedDirectory, new PrintWriter(out, true));
        }

        private static Object discoveryRequest(Path testOutputDirectory, TestSelection testSelection)
                throws ReflectiveOperationException {
            TestSelection selection = testSelection == null ? TestSelection.empty() : testSelection;
            Class<?> selectorsClass = Class.forName("org.junit.platform.engine.discovery.DiscoverySelectors");
            Object selectors = selectors(selection, selectorsClass, testOutputDirectory);

            Class<?> builderClass = Class.forName("org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder");
            Object builder = builderClass.getMethod("request").invoke(null);
            builderClass.getMethod("selectors", List.class).invoke(builder, selectors);
            applyFilters(builderClass, builder, selection);
            return builderClass.getMethod("build").invoke(builder);
        }

        private static Object selectors(
                TestSelection selection,
                Class<?> selectorsClass,
                Path testOutputDirectory) throws ReflectiveOperationException {
            boolean hasClassOrMethodSelectors =
                    !selection.classSelectors().isEmpty() || !selection.methodSelectors().isEmpty();
            if (!hasClassOrMethodSelectors) {
                return selectorsClass
                        .getMethod("selectClasspathRoots", Set.class)
                        .invoke(null, Set.of(testOutputDirectory));
            }
            List<Object> selectors = new ArrayList<>();
            Method selectClass = selectorsClass.getMethod("selectClass", String.class);
            Method selectMethod = selectorsClass.getMethod("selectMethod", String.class);
            for (String classSelector : selection.classSelectors()) {
                selectors.add(selectClass.invoke(null, classSelector));
            }
            for (TestSelection.MethodSelector methodSelector : selection.methodSelectors()) {
                selectors.add(selectMethod.invoke(null, methodSelector.className() + "#" + methodSelector.methodName()));
            }
            return selectors;
        }

        private static void applyFilters(Class<?> builderClass, Object builder, TestSelection selection)
                throws ReflectiveOperationException {
            List<Object> filters = new ArrayList<>();
            addClassNameFilters(filters, selection);
            addTagFilters(filters, selection);
            if (filters.isEmpty()) {
                return;
            }
            Class<?> filterInterface = Class.forName("org.junit.platform.engine.Filter");
            Object filterArray = Array.newInstance(filterInterface, filters.size());
            for (int index = 0; index < filters.size(); index++) {
                Array.set(filterArray, index, filters.get(index));
            }
            builderClass
                    .getMethod("filters", filterArray.getClass())
                    .invoke(builder, filterArray);
        }

        private static void addClassNameFilters(List<Object> filters, TestSelection selection)
                throws ReflectiveOperationException {
            Class<?> classNameFilterClass = Class.forName("org.junit.platform.engine.discovery.ClassNameFilter");
            List<String> patterns = selection.classNameRegexPatterns();
            if (patterns.isEmpty()
                    && selection.classSelectors().isEmpty()
                    && selection.methodSelectors().isEmpty()) {
                patterns = TestSelection.defaultScanClassNamePatterns();
            }
            if (patterns.isEmpty()) {
                return;
            }
            Object classNameFilter = classNameFilterClass
                    .getMethod("includeClassNamePatterns", String[].class)
                    .invoke(null, (Object) patterns.toArray(String[]::new));
            filters.add(classNameFilter);
        }

        private static void addTagFilters(List<Object> filters, TestSelection selection)
                throws ReflectiveOperationException {
            Class<?> tagFilterClass = Class.forName("org.junit.platform.launcher.TagFilter");
            if (!selection.includedTags().isEmpty()) {
                filters.add(tagFilterClass
                        .getMethod("includeTags", String[].class)
                        .invoke(null, (Object) selection.includedTags().toArray(String[]::new)));
            }
            if (!selection.excludedTags().isEmpty()) {
                filters.add(tagFilterClass
                        .getMethod("excludeTags", String[].class)
                        .invoke(null, (Object) selection.excludedTags().toArray(String[]::new)));
            }
        }

        private int summarize(Class<?> listenerClass, Object listener) throws ReflectiveOperationException {
            Object summary = listenerClass.getMethod("getSummary").invoke(listener);
            Class<?> summaryClass = Class.forName("org.junit.platform.launcher.listeners.TestExecutionSummary");
            long found = (long) summaryClass.getMethod("getTestsFoundCount").invoke(summary);
            long succeeded = (long) summaryClass.getMethod("getTestsSucceededCount").invoke(summary);
            long failed = (long) summaryClass.getMethod("getTestsFailedCount").invoke(summary);
            long aborted = (long) summaryClass.getMethod("getTestsAbortedCount").invoke(summary);
            long totalFailures = (long) summaryClass.getMethod("getTotalFailureCount").invoke(summary);
            out.println("Tests found: " + found);
            out.println("Tests succeeded: " + succeeded);
            out.println("Tests failed: " + failed);
            if (totalFailures > 0) {
                out.println();
                summaryClass
                        .getMethod("printFailuresTo", PrintWriter.class)
                        .invoke(summary, new PrintWriter(out, true));
            }
            if (found == 0) {
                return 2;
            }
            return failed == 0 && aborted == 0 ? 0 : 1;
        }
    }

}
