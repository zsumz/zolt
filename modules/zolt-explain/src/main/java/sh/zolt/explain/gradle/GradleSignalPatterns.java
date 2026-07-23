package sh.zolt.explain.gradle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GradleSignalPatterns {
    private GradleSignalPatterns() {
    }

    static boolean isConventionPlugin(GradlePluginInspection plugin) {
        String lower = plugin.id().toLowerCase();
        return lower.contains("convention")
                || lower.contains("build-logic")
                || lower.contains("build-metadata")
                || lower.startsWith("junitbuild.")
                || lower.startsWith("caffeine.")
                || lower.contains(".caffeine.");
    }

    static boolean usesEnvironmentVariable(String content) {
        return Pattern.compile("\\bSystem\\.getenv\\s*\\(|\\bproviders\\.environmentVariable\\s*\\(")
                .matcher(content)
                .find();
    }

    static boolean appliesScriptPlugin(String content) {
        return Pattern.compile("(?m)^\\s*apply\\s+(?:from\\s*:|\\(\\s*from\\s*=)")
                .matcher(content)
                .find();
    }

    static boolean hasConditionalPluginApply(String content) {
        for (Range range : conditionalBlockRanges(content)) {
            String block = content.substring(range.start(), range.end());
            if (Pattern.compile("(?m)^\\s*apply\\s+(?:plugin\\s*:|\\(\\s*plugin\\s*=)").matcher(block).find()) {
                return true;
            }
        }
        return false;
    }

    static boolean hasStartParameterSelection(String content) {
        return Pattern.compile("\\bgradle\\.startParameter\\b|\\bstartParameter\\.(?:excludedTaskNames|taskNames)\\b")
                .matcher(content)
                .find();
    }

    static boolean hasTaskMutation(String content) {
        return Pattern.compile("\\btasks\\.named\\s*\\([^)]*\\)\\s*\\.configure\\b|\\benabled\\s*=\\s*false\\b")
                .matcher(content)
                .find();
    }

    static boolean hasPublicationConfiguration(String content) {
        return (content.contains("publishing") && containsAny(content,
                "MavenPublication",
                "publications {",
                "publishing.publications",
                "mavenJava",
                "create(\"maven",
                "create('maven"))
                || containsAny(content,
                        "com.vanniktech.maven.publish",
                        "mavenPublishing",
                        "signing {");
    }

    static boolean hasTestRuntimeSettings(String content) {
        boolean testTask = Pattern.compile("\\btest\\s*\\{|\\btasks\\.named\\s*\\(\\s*['\"]test['\"]|\\btasks\\.withType\\s*\\(\\s*Test\\b|\\btasks\\.withType\\s*<\\s*Test\\s*>")
                .matcher(content)
                .find();
        return testTask && containsAny(content, "systemProperty", "environment", "jvmArgs", "jvmArgumentProviders", "testLogging");
    }

    static List<Range> environmentConditionalBlockRanges(String content) {
        List<Range> ranges = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\bif\\s*\\(").matcher(content);
        while (matcher.find()) {
            int openBrace = content.indexOf('{', matcher.end());
            if (openBrace < 0) {
                continue;
            }
            String header = content.substring(matcher.start(), openBrace);
            if (usesEnvironmentVariable(header)) {
                ranges.add(new Range(openBrace + 1, matchingBraceEnd(content, openBrace)));
            }
        }
        return ranges;
    }

    private static final Pattern EXEC_TASK = Pattern.compile(
            "\\b(?:register|create)\\s*\\(\\s*['\"][^'\"]+['\"]\\s*,\\s*(Exec|JavaExec)\\b"
                    + "|\\btask\\s+\\w+\\s*\\(\\s*type\\s*[:=]\\s*(Exec|JavaExec)\\b"
                    + "|\\bregister\\s*<\\s*(Exec|JavaExec)\\s*>"
                    + "|\\btype\\s*=\\s*(Exec|JavaExec)\\b");

    /**
     * Finds {@code Exec}/{@code JavaExec} task declarations and classifies each by its body: a single
     * command (mappable to a Zolt exec step) versus a scripted body with task actions, control flow, or
     * shell operators (unmappable). Detection stays within the regex/known-shape idiom — it reads the
     * task type and a coarse shape, never the full command.
     */
    static List<ExecTask> execTasks(String content) {
        List<ExecTask> tasks = new ArrayList<>();
        Matcher matcher = EXEC_TASK.matcher(content);
        while (matcher.find()) {
            String type = matchedType(matcher);
            int openBrace = content.indexOf('{', matcher.end());
            String block = openBrace >= 0
                    ? content.substring(openBrace + 1, matchingBraceEnd(content, openBrace))
                    : "";
            tasks.add(new ExecTask(type, hasExecScriptBody(block)));
        }
        return tasks;
    }

    private static String matchedType(Matcher matcher) {
        for (int group = 1; group <= matcher.groupCount(); group++) {
            if (matcher.group(group) != null) {
                return matcher.group(group);
            }
        }
        return "Exec";
    }

    private static boolean hasExecScriptBody(String block) {
        if (containsAny(block, "doFirst", "doLast", "providers.exec", "project.exec", "exec {", "exec(")) {
            return true;
        }
        if (Pattern.compile("\\b(?:if|for|while)\\s*\\(").matcher(block).find()) {
            return true;
        }
        return containsAny(block, "&&", "||", "$(", "`", "| ");
    }

    static boolean isInsideAny(int index, List<Range> ranges) {
        for (Range range : ranges) {
            if (index >= range.start() && index < range.end()) {
                return true;
            }
        }
        return false;
    }

    private static List<Range> conditionalBlockRanges(String content) {
        List<Range> ranges = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\bif\\s*\\(").matcher(content);
        while (matcher.find()) {
            int openBrace = content.indexOf('{', matcher.end());
            if (openBrace >= 0) {
                ranges.add(new Range(openBrace + 1, matchingBraceEnd(content, openBrace)));
            }
        }
        return ranges;
    }

    private static int matchingBraceEnd(String content, int openBrace) {
        int depth = 0;
        for (int index = openBrace; index < content.length(); index++) {
            char character = content.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return content.length();
    }

    private static boolean containsAny(String content, String... values) {
        for (String value : values) {
            if (content.contains(value)) {
                return true;
            }
        }
        return false;
    }

    record Range(int start, int end) {
    }

    record ExecTask(String type, boolean unmappable) {
    }
}
