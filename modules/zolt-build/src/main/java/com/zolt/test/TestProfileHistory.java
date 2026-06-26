package com.zolt.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record TestProfileHistory(
        Optional<Path> source,
        Map<String, Long> classDurations,
        List<String> diagnostics) {
    private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern NUMBER_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(-?\\d+)");

    public TestProfileHistory {
        source = source == null ? Optional.empty() : source;
        classDurations = classDurations == null ? Map.of() : Map.copyOf(classDurations);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static TestProfileHistory none() {
        return new TestProfileHistory(Optional.empty(), Map.of(), List.of());
    }

    public static TestProfileHistory read(Path projectRoot, Path profileJson) {
        if (profileJson == null) {
            return none();
        }
        Path source = (profileJson.isAbsolute() ? profileJson : projectRoot.resolve(profileJson))
                .toAbsolutePath()
                .normalize();
        if (!Files.exists(source)) {
            return new TestProfileHistory(
                    Optional.of(source),
                    Map.of(),
                    List.of("Profile history `" + source + "` does not exist; using deterministic round-robin sharding."));
        }
        try {
            Map<String, Long> durations = classDurations(Files.readString(source));
            List<String> diagnostics = durations.isEmpty()
                    ? List.of("Profile history `" + source + "` does not contain class-level durations; using deterministic round-robin sharding.")
                    : List.of();
            return new TestProfileHistory(Optional.of(source), durations, diagnostics);
        } catch (IOException exception) {
            return new TestProfileHistory(
                    Optional.of(source),
                    Map.of(),
                    List.of("Profile history `" + source + "` could not be read; using deterministic round-robin sharding."));
        }
    }

    public boolean requested() {
        return source.isPresent();
    }

    private static Map<String, Long> classDurations(String json) {
        Map<String, Long> durations = new LinkedHashMap<>();
        for (String object : entries(json, "containers")) {
            String className = string(object, "className");
            long duration = number(object, "durationMillis");
            if (!className.isBlank() && duration > 0L) {
                durations.merge(className, duration, Long::sum);
            }
        }
        return durations;
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

    private static String string(String json, String fieldName) {
        Matcher matcher = Pattern.compile(String.format(STRING_FIELD.pattern(), Pattern.quote(fieldName))).matcher(json);
        return matcher.find() ? matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : "";
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
}
