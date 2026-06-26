package com.zolt.junit;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class JunitTestProfileCollector {
    private final Path profileDirectory;
    private final String workerId = Optional.ofNullable(System.getenv("ZOLT_TEST_WORKER_ID")).orElse("");
    private final long runStartedNanos = System.nanoTime();
    private final Map<String, StartedEntry> started = new ConcurrentHashMap<>();
    private final List<ProfileEntry> tests = java.util.Collections.synchronizedList(new ArrayList<>());
    private final List<ProfileEntry> containers = java.util.Collections.synchronizedList(new ArrayList<>());

    JunitTestProfileCollector(Path profileDirectory) {
        this.profileDirectory = profileDirectory.toAbsolutePath().normalize();
    }

    Object listener(Class<?> listenerInterface) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("toString".equals(name) && method.getParameterCount() == 0) {
                return "ZoltTestProfileListener";
            }
            if ("hashCode".equals(name) && method.getParameterCount() == 0) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name) && method.getParameterCount() == 1) {
                return proxy == args[0];
            }
            if ("executionStarted".equals(name) && args != null && args.length == 1) {
                executionStarted(args[0]);
            } else if ("executionSkipped".equals(name) && args != null && args.length == 2) {
                executionSkipped(args[0]);
            } else if ("executionFinished".equals(name) && args != null && args.length == 2) {
                executionFinished(args[0], args[1]);
            }
            return null;
        };
        return Proxy.newProxyInstance(
                listenerInterface.getClassLoader(),
                new Class<?>[] {listenerInterface},
                handler);
    }

    void write() throws IOException {
        Files.createDirectories(profileDirectory);
        Files.writeString(profileDirectory.resolve("profile.json"), json());
    }

    private void executionStarted(Object identifier) throws ReflectiveOperationException {
        TestIdentity identity = TestIdentity.from(identifier);
        started.put(identity.uniqueId(), new StartedEntry(identity, System.nanoTime()));
    }

    private void executionSkipped(Object identifier) throws ReflectiveOperationException {
        TestIdentity identity = TestIdentity.from(identifier);
        add(new ProfileEntry(identity, "skipped", 0L, workerId));
    }

    private void executionFinished(Object identifier, Object result) throws ReflectiveOperationException {
        TestIdentity identity = TestIdentity.from(identifier);
        StartedEntry entry = started.remove(identity.uniqueId());
        long startedNanos = entry == null ? System.nanoTime() : entry.startedNanos();
        long durationNanos = Math.max(0L, System.nanoTime() - startedNanos);
        add(new ProfileEntry(identity, status(result), durationNanos / 1_000_000L, workerId));
    }

    private void add(ProfileEntry entry) {
        if (entry.identity().test()) {
            tests.add(entry);
        } else if (entry.identity().container()) {
            containers.add(entry);
        }
    }

    private String json() {
        List<ProfileEntry> sortedTests = sortedTests();
        List<ProfileEntry> sortedContainers = sortedContainers();
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, 1, "schemaVersion", "1", true);
        field(json, 1, "runner", quote("zolt-junit-worker"), true);
        field(json, 1, "workerId", quote(workerId), true);
        summary(json, sortedTests);
        entries(json, "tests", sortedTests, true);
        entries(json, "containers", withTestCounts(sortedContainers, sortedTests), false);
        json.append("}\n");
        return json.toString();
    }

    private List<ProfileEntry> sortedTests() {
        synchronized (tests) {
            return tests.stream()
                    .sorted(Comparator
                            .comparing((ProfileEntry entry) -> entry.identity().className())
                            .thenComparing(entry -> entry.identity().methodName())
                            .thenComparing(entry -> entry.identity().uniqueId())
                            .thenComparing(ProfileEntry::workerId))
                    .toList();
        }
    }

    private List<ProfileEntry> sortedContainers() {
        synchronized (containers) {
            return containers.stream()
                    .sorted(Comparator
                            .comparing((ProfileEntry entry) -> entry.identity().className())
                            .thenComparing(entry -> entry.identity().uniqueId())
                            .thenComparing(ProfileEntry::workerId))
                    .toList();
        }
    }

    private List<ProfileEntry> withTestCounts(List<ProfileEntry> containerEntries, List<ProfileEntry> testEntries) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ProfileEntry test : testEntries) {
            counts.merge(test.identity().className(), 1, Integer::sum);
        }
        return containerEntries.stream()
                .map(entry -> entry.withTestCount(counts.getOrDefault(entry.identity().className(), 0)))
                .toList();
    }

    private void summary(StringBuilder json, List<ProfileEntry> entries) {
        long succeeded = entries.stream().filter(entry -> entry.status().equals("passed")).count();
        long failed = entries.stream().filter(entry -> entry.status().equals("failed")).count();
        long skipped = entries.stream().filter(entry -> entry.status().equals("skipped")).count();
        long aborted = entries.stream().filter(entry -> entry.status().equals("aborted")).count();
        indent(json, 1).append("\"summary\": {\n");
        field(json, 2, "testsFound", Long.toString(entries.size()), true);
        field(json, 2, "testsSucceeded", Long.toString(succeeded), true);
        field(json, 2, "testsFailed", Long.toString(failed), true);
        field(json, 2, "testsSkipped", Long.toString(skipped), true);
        field(json, 2, "testsAborted", Long.toString(aborted), true);
        field(json, 2, "durationMillis", Long.toString((System.nanoTime() - runStartedNanos) / 1_000_000L), false);
        indent(json, 1).append("},\n");
    }

    private static void entries(
            StringBuilder json,
            String fieldName,
            List<ProfileEntry> entries,
            boolean comma) {
        indent(json, 1).append("\"").append(fieldName).append("\": [");
        if (!entries.isEmpty()) {
            json.append("\n");
            for (int index = 0; index < entries.size(); index++) {
                entry(json, entries.get(index), index + 1 < entries.size());
            }
            indent(json, 1).append("]");
        } else {
            json.append("]");
        }
        if (comma) {
            json.append(",");
        }
        json.append("\n");
    }

    private static void entry(StringBuilder json, ProfileEntry entry, boolean comma) {
        indent(json, 2).append("{\n");
        TestIdentity identity = entry.identity();
        field(json, 3, "uniqueId", quote(identity.uniqueId()), true);
        field(json, 3, "engineId", quote(identity.engineId()), true);
        field(json, 3, "className", quote(identity.className()), true);
        field(json, 3, "methodName", quote(identity.methodName()), true);
        field(json, 3, "displayName", quote(identity.displayName()), true);
        field(json, 3, "status", quote(entry.status()), true);
        field(json, 3, "durationMillis", Long.toString(entry.durationMillis()), true);
        field(json, 3, "workerId", quote(entry.workerId()), entry.testCount() >= 0);
        if (entry.testCount() >= 0) {
            field(json, 3, "testCount", Integer.toString(entry.testCount()), false);
        }
        indent(json, 2).append("}");
        if (comma) {
            json.append(",");
        }
        json.append("\n");
    }

    private static void field(StringBuilder json, int depth, String name, String value, boolean comma) {
        indent(json, depth).append("\"").append(name).append("\": ").append(value);
        if (comma) {
            json.append(",");
        }
        json.append("\n");
    }

    private static StringBuilder indent(StringBuilder json, int depth) {
        return json.append("  ".repeat(depth));
    }

    private static String status(Object result) throws ReflectiveOperationException {
        Object status = result.getClass().getMethod("getStatus").invoke(result);
        return switch (status.toString()) {
            case "SUCCESSFUL" -> "passed";
            case "ABORTED" -> "aborted";
            case "FAILED" -> "failed";
            default -> status.toString().toLowerCase(java.util.Locale.ROOT);
        };
    }

    private static String quote(String value) {
        String text = value == null ? "" : value;
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    private record StartedEntry(TestIdentity identity, long startedNanos) {
    }

    private record ProfileEntry(
            TestIdentity identity,
            String status,
            long durationMillis,
            String workerId,
            int testCount) {
        private ProfileEntry(TestIdentity identity, String status, long durationMillis, String workerId) {
            this(identity, status, durationMillis, workerId, -1);
        }

        private ProfileEntry withTestCount(int value) {
            return new ProfileEntry(identity, status, durationMillis, workerId, value);
        }
    }

    private record TestIdentity(
            String uniqueId,
            String engineId,
            String className,
            String methodName,
            String displayName,
            boolean test,
            boolean container) {
        private static TestIdentity from(Object identifier) throws ReflectiveOperationException {
            String uniqueId = stringMethod(identifier, "getUniqueId");
            SourceParts source = sourceParts(identifier);
            return new TestIdentity(
                    uniqueId,
                    engineId(uniqueId),
                    source.className(),
                    source.methodName(),
                    stringMethod(identifier, "getDisplayName"),
                    booleanMethod(identifier, "isTest"),
                    booleanMethod(identifier, "isContainer"));
        }

        private static SourceParts sourceParts(Object identifier) throws ReflectiveOperationException {
            Optional<?> source = (Optional<?>) identifier.getClass().getMethod("getSource").invoke(identifier);
            if (source.isEmpty()) {
                return new SourceParts("", "");
            }
            Object value = source.orElseThrow();
            String typeName = value.getClass().getName();
            if (typeName.endsWith(".MethodSource")) {
                return new SourceParts(
                        stringMethod(value, "getClassName"),
                        stringMethod(value, "getMethodName"));
            }
            if (typeName.endsWith(".ClassSource")) {
                return new SourceParts(stringMethod(value, "getClassName"), "");
            }
            return new SourceParts("", "");
        }

        private static String engineId(String uniqueId) {
            String prefix = "[engine:";
            if (uniqueId == null || !uniqueId.startsWith(prefix)) {
                return "";
            }
            int end = uniqueId.indexOf(']', prefix.length());
            return end < 0 ? "" : uniqueId.substring(prefix.length(), end);
        }

        private static String stringMethod(Object target, String method) throws ReflectiveOperationException {
            Object value = target.getClass().getMethod(method).invoke(target);
            return value == null ? "" : value.toString();
        }

        private static boolean booleanMethod(Object target, String method) throws ReflectiveOperationException {
            Object value = target.getClass().getMethod(method).invoke(target);
            return Boolean.TRUE.equals(value);
        }
    }

    private record SourceParts(String className, String methodName) {
    }
}
