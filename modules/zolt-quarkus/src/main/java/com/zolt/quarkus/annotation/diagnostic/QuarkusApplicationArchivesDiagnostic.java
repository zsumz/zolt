package com.zolt.quarkus.annotation.diagnostic;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

final class QuarkusApplicationArchivesDiagnostic {
    private QuarkusApplicationArchivesDiagnostic() {
    }

    static String formatArchives(Object applicationArchives, Path testOutputDirectory) {
        if (applicationArchives == null) {
            return "<missing>";
        }
        Collection<?> archives = collection(applicationArchives, "getAllApplicationArchives");
        if (archives == null) {
            return "<unavailable>";
        }
        return "count="
                + archives.size()
                + ",testOutputArchive="
                + containsRoot(archives, testOutputDirectory);
    }

    static String formatSelectedClassArchives(
            Object applicationArchives,
            List<String> testClasses) {
        if (applicationArchives == null) {
            return "<missing>";
        }
        if (testClasses.isEmpty()) {
            return "<none>";
        }
        return testClasses.stream()
                .map(testClass -> testClass + "[archive=" + archiveLocation(containingArchive(applicationArchives, testClass)) + "]")
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static Object containingArchive(Object applicationArchives, String testClass) {
        try {
            Method method = applicationArchives.getClass().getMethod("containingArchive", String.class);
            return method.invoke(applicationArchives, testClass);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }

    private static boolean containsRoot(Collection<?> archives, Path testOutputDirectory) {
        if (testOutputDirectory == null) {
            return false;
        }
        Path normalized = testOutputDirectory.toAbsolutePath().normalize();
        return archives.stream().anyMatch(archive -> roots(archive).stream().anyMatch(normalized::equals));
    }

    private static String archiveLocation(Object archive) {
        if (archive == null) {
            return "<missing>";
        }
        List<Path> roots = roots(archive);
        if (!roots.isEmpty()) {
            return joinedPaths(roots);
        }
        Object location = invoke(archive, "getArchiveLocation");
        return location == null ? "<unavailable>" : String.valueOf(location);
    }

    private static List<Path> roots(Object archive) {
        Object roots = invoke(archive, "getRootDirectories");
        if (!(roots instanceof Iterable<?> iterable)) {
            roots = invoke(archive, "getRootDirs");
        }
        if (!(roots instanceof Iterable<?> iterable)) {
            return List.of();
        }
        java.util.ArrayList<Path> paths = new java.util.ArrayList<>();
        for (Object root : iterable) {
            if (root instanceof Path path) {
                paths.add(path.toAbsolutePath().normalize());
            }
        }
        return paths;
    }

    private static Collection<?> collection(Object target, String methodName) {
        Object value = invoke(target, methodName);
        return value instanceof Collection<?> collection ? collection : null;
    }

    private static Object invoke(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }

    private static String joinedPaths(List<Path> paths) {
        return paths.stream().map(Path::toString).collect(java.util.stream.Collectors.joining("|"));
    }
}
