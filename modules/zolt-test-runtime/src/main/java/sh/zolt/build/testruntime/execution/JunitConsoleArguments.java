package sh.zolt.build.testruntime.execution;

import sh.zolt.test.TestSelection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

final class JunitConsoleArguments {
    private final String pathSeparator;

    JunitConsoleArguments(String pathSeparator) {
        this.pathSeparator = pathSeparator;
    }

    List<String> arguments(
            List<Path> runnerClasspath,
            Path testOutputDirectory,
            TestSelection selection,
            Optional<Path> reportsDirectory,
            List<String> events) {
        List<String> arguments = new ArrayList<>();
        arguments.add("execute");
        arguments.add("--disable-banner");
        arguments.add("--class-path");
        arguments.add(joined(runnerClasspath));
        addSelectors(arguments, testOutputDirectory.toAbsolutePath().normalize(), selection);
        reportsDirectory.ifPresent(directory -> {
            arguments.add("--reports-dir");
            arguments.add(directory.toString());
        });
        arguments.add("--details");
        if (events == null || events.isEmpty()) {
            arguments.add("summary");
        } else {
            arguments.add("tree");
            arguments.add("--details-theme");
            arguments.add("ascii");
        }
        return List.copyOf(arguments);
    }

    private void addSelectors(
            List<String> arguments,
            Path testOutputDirectory,
            TestSelection selection) {
        boolean hasClassOrMethodSelectors =
                !selection.classSelectors().isEmpty() || !selection.methodSelectors().isEmpty();
        if (!hasClassOrMethodSelectors) {
            arguments.add("--scan-class-path=" + testOutputDirectory);
        }
        for (String classSelector : selection.classSelectors()) {
            arguments.add("--select-class");
            arguments.add(classSelector);
        }
        for (TestSelection.MethodSelector methodSelector : selection.methodSelectors()) {
            arguments.add("--select-method");
            arguments.add(methodSelector.className() + "#" + methodSelector.methodName());
        }
        List<String> classNamePatterns = selection.classNamePatterns().isEmpty() && !hasClassOrMethodSelectors
                ? TestSelection.defaultScanClassNamePatterns()
                : selection.classNameRegexPatterns();
        for (String pattern : classNamePatterns) {
            arguments.add("--include-classname");
            arguments.add(pattern);
        }
        for (String tag : selection.includedTags()) {
            arguments.add("--include-tag");
            arguments.add(tag);
        }
        for (String tag : selection.excludedTags()) {
            arguments.add("--exclude-tag");
            arguments.add(tag);
        }
    }

    private String joined(List<Path> classpath) {
        StringJoiner joiner = new StringJoiner(pathSeparator);
        for (Path entry : classpath) {
            joiner.add(entry.normalize().toString());
        }
        return joiner.toString();
    }
}
