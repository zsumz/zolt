package com.zolt.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TestProfileMerger {
    private static final Pattern NUMBER_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(-?\\d+)");

    private TestProfileMerger() {
    }

    public static void mergeWorkerProfiles(Path profileDirectory, List<String> workerIds) {
        if (profileDirectory == null || workerIds == null || workerIds.isEmpty()) {
            return;
        }
        Path profileRoot = profileDirectory.toAbsolutePath().normalize();
        List<String> tests = new ArrayList<>();
        List<String> containers = new ArrayList<>();
        Summary summary = Summary.empty();
        for (String workerId : workerIds) {
            Path workerProfile = profileRoot.resolve("workers").resolve(workerId).resolve("profile.json");
            if (!Files.exists(workerProfile)) {
                continue;
            }
            try {
                String json = Files.readString(workerProfile);
                tests.addAll(entries(json, "tests"));
                containers.addAll(entries(json, "containers"));
                summary = summary.plus(summary(json));
            } catch (IOException exception) {
                throw new TestRunException("Could not read test profile " + workerProfile + ".", exception);
            }
        }
        if (tests.isEmpty() && containers.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(profileRoot);
            Files.writeString(profileRoot.resolve("profile.json"), mergedJson(summary, tests, containers));
        } catch (IOException exception) {
            throw new TestRunException("Could not write merged test profile to " + profileRoot.resolve("profile.json") + ".", exception);
        }
    }

    private static Summary summary(String json) {
        return new Summary(
                number(json, "testsFound"),
                number(json, "testsSucceeded"),
                number(json, "testsFailed"),
                number(json, "testsSkipped"),
                number(json, "testsAborted"),
                number(json, "durationMillis"));
    }

    private static List<String> entries(String json, String fieldName) {
        return arrayBody(json, fieldName).stream()
                .flatMap(body -> objectBodies(body).stream())
                .toList();
    }

    private static Optional<String> arrayBody(String json, String fieldName) {
        int field = json.indexOf("\"" + fieldName + "\"");
        if (field < 0) {
            return Optional.empty();
        }
        int open = json.indexOf('[', field);
        if (open < 0) {
            return Optional.empty();
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = open; index < json.length(); index++) {
            char ch = json.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return Optional.of(json.substring(open + 1, index));
                }
            }
        }
        return Optional.empty();
    }

    private static List<String> objectBodies(String arrayBody) {
        List<String> objects = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int open = -1;
        for (int index = 0; index < arrayBody.length(); index++) {
            char ch = arrayBody.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                if (depth == 0) {
                    open = index;
                }
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && open >= 0) {
                    objects.add(arrayBody.substring(open, index + 1));
                }
            }
        }
        return objects;
    }

    private static long number(String json, String fieldName) {
        Matcher matcher = Pattern.compile(String.format(NUMBER_FIELD.pattern(), Pattern.quote(fieldName))).matcher(json);
        if (!matcher.find()) {
            return 0L;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static String mergedJson(Summary summary, List<String> tests, List<String> containers) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, 1, "schemaVersion", "1", true);
        field(json, 1, "runner", quote("zolt-junit-worker"), true);
        field(json, 1, "workerId", quote(""), true);
        json.append("  \"summary\": {\n");
        field(json, 2, "testsFound", Long.toString(summary.testsFound()), true);
        field(json, 2, "testsSucceeded", Long.toString(summary.testsSucceeded()), true);
        field(json, 2, "testsFailed", Long.toString(summary.testsFailed()), true);
        field(json, 2, "testsSkipped", Long.toString(summary.testsSkipped()), true);
        field(json, 2, "testsAborted", Long.toString(summary.testsAborted()), true);
        field(json, 2, "durationMillis", Long.toString(summary.durationMillis()), false);
        json.append("  },\n");
        entries(json, "tests", tests, true);
        entries(json, "containers", containers, false);
        json.append("}\n");
        return json.toString();
    }

    private static void entries(StringBuilder json, String fieldName, List<String> entries, boolean comma) {
        json.append("  \"").append(fieldName).append("\": [");
        if (!entries.isEmpty()) {
            json.append("\n");
            for (int index = 0; index < entries.size(); index++) {
                json.append(indent(entries.get(index), 2));
                if (index + 1 < entries.size()) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append("  ]");
        } else {
            json.append("]");
        }
        if (comma) {
            json.append(",");
        }
        json.append("\n");
    }

    private static String indent(String text, int depth) {
        String indentation = "  ".repeat(depth);
        return indentation + text.strip().replace("\n", "\n" + indentation);
    }

    private static void field(StringBuilder json, int depth, String name, String value, boolean comma) {
        json.append("  ".repeat(depth)).append("\"").append(name).append("\": ").append(value);
        if (comma) {
            json.append(",");
        }
        json.append("\n");
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private record Summary(
            long testsFound,
            long testsSucceeded,
            long testsFailed,
            long testsSkipped,
            long testsAborted,
            long durationMillis) {
        private static Summary empty() {
            return new Summary(0L, 0L, 0L, 0L, 0L, 0L);
        }

        private Summary plus(Summary other) {
            return new Summary(
                    testsFound + other.testsFound(),
                    testsSucceeded + other.testsSucceeded(),
                    testsFailed + other.testsFailed(),
                    testsSkipped + other.testsSkipped(),
                    testsAborted + other.testsAborted(),
                    durationMillis + other.durationMillis());
        }
    }
}
