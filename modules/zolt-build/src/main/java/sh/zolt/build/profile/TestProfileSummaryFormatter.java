package sh.zolt.build.profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TestProfileSummaryFormatter {
    private static final Pattern STRING_FIELD = Pattern.compile(
            "\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
            Pattern.DOTALL);
    private static final Pattern NUMBER_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(-?\\d+)");

    private TestProfileSummaryFormatter() {
    }

    public static Optional<String> format(Path profileJson, TestProfileSettings settings) {
        if (settings == null || !settings.enabled() || profileJson == null || !Files.exists(profileJson)) {
            return Optional.empty();
        }
        try {
            return format(Files.readString(profileJson), settings);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    static Optional<String> format(String profileJson, TestProfileSettings settings) {
        if (!validProfile(profileJson)) {
            return Optional.empty();
        }
        List<ProfileEntry> tests = entries(profileJson, "tests").stream()
                .filter(ProfileEntry::hasTestLabel)
                .toList();
        List<ProfileEntry> classes = entries(profileJson, "containers").stream()
                .filter(ProfileEntry::hasClassLabel)
                .filter(entry -> entry.testCount() != 0)
                .toList();
        List<String> sections = new ArrayList<>();
        rankedSection("Slowest tests", tests, settings, ProfileEntry::testLabel).ifPresent(sections::add);
        rankedSection("Slowest classes", classes, settings, ProfileEntry::classLabel).ifPresent(sections::add);
        workerSection(tests).ifPresent(sections::add);
        if (sections.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(String.join(System.lineSeparator() + System.lineSeparator(), sections));
    }

    private static boolean validProfile(String profileJson) {
        return number(profileJson, "schemaVersion") == 1L
                && arrayBody(profileJson, "tests").isPresent()
                && arrayBody(profileJson, "containers").isPresent();
    }

    private static Optional<String> rankedSection(
            String title,
            List<ProfileEntry> entries,
            TestProfileSettings settings,
            java.util.function.Function<ProfileEntry, String> labeler) {
        List<ProfileEntry> ranked = entries.stream()
                .filter(entry -> entry.durationMillis() >= settings.minimumDurationMillis())
                .sorted(ProfileEntry.SLOWEST_FIRST)
                .limit(settings.summaryLimit())
                .toList();
        if (ranked.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder output = new StringBuilder(title).append(":");
        for (ProfileEntry entry : ranked) {
            output.append(System.lineSeparator())
                    .append("  ")
                    .append(entry.durationMillis())
                    .append(" ms ")
                    .append(labeler.apply(entry));
        }
        return Optional.of(output.toString());
    }

    private static Optional<String> workerSection(List<ProfileEntry> tests) {
        Map<String, WorkerSummary> workers = new LinkedHashMap<>();
        for (ProfileEntry test : tests) {
            if (!test.workerId().isBlank()) {
                workers.merge(
                        test.workerId(),
                        new WorkerSummary(test.durationMillis(), 1),
                        WorkerSummary::plus);
            }
        }
        if (workers.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder output = new StringBuilder("Worker balance:");
        workers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> output.append(System.lineSeparator())
                        .append("  ")
                        .append(entry.getValue().durationMillis())
                        .append(" ms ")
                        .append(entry.getKey())
                        .append(" (")
                        .append(entry.getValue().testCount())
                        .append(entry.getValue().testCount() == 1 ? " test)" : " tests)"));
        return Optional.of(output.toString());
    }

    private static List<ProfileEntry> entries(String json, String fieldName) {
        return arrayBody(json, fieldName).stream()
                .flatMap(body -> objectBodies(body).stream())
                .map(TestProfileSummaryFormatter::entry)
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

    private static ProfileEntry entry(String json) {
        return new ProfileEntry(
                string(json, "uniqueId"),
                string(json, "className"),
                string(json, "methodName"),
                string(json, "displayName"),
                string(json, "workerId"),
                number(json, "durationMillis"),
                (int) number(json, "testCount"));
    }

    private static String string(String json, String fieldName) {
        Matcher matcher = Pattern.compile(String.format(STRING_FIELD.pattern(), Pattern.quote(fieldName)), Pattern.DOTALL)
                .matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : "";
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

    private static String unescape(String value) {
        StringBuilder text = new StringBuilder();
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!escaped && ch == '\\') {
                escaped = true;
                continue;
            }
            if (escaped) {
                text.append(switch (ch) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '/' -> '/';
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> ch;
                });
                escaped = false;
            } else {
                text.append(ch);
            }
        }
        if (escaped) {
            text.append('\\');
        }
        return text.toString();
    }

    private record ProfileEntry(
            String uniqueId,
            String className,
            String methodName,
            String displayName,
            String workerId,
            long durationMillis,
            int testCount) {
        private static final Comparator<ProfileEntry> SLOWEST_FIRST = Comparator
                .comparingLong(ProfileEntry::durationMillis)
                .reversed()
                .thenComparing(ProfileEntry::className)
                .thenComparing(ProfileEntry::methodName)
                .thenComparing(ProfileEntry::displayName)
                .thenComparing(ProfileEntry::uniqueId)
                .thenComparing(ProfileEntry::workerId);

        private boolean hasTestLabel() {
            return !className.isBlank() || !displayName.isBlank();
        }

        private boolean hasClassLabel() {
            return !className.isBlank();
        }

        private String testLabel() {
            if (!className.isBlank() && !methodName.isBlank()) {
                return className + "#" + methodName;
            }
            if (!className.isBlank()) {
                return className;
            }
            return displayName;
        }

        private String classLabel() {
            return className + " (" + testCount + (testCount == 1 ? " test)" : " tests)");
        }
    }

    private record WorkerSummary(long durationMillis, int testCount) {
        private WorkerSummary plus(WorkerSummary other) {
            return new WorkerSummary(durationMillis + other.durationMillis(), testCount + other.testCount());
        }
    }
}
