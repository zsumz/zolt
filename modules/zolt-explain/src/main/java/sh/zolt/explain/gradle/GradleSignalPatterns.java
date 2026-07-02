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
}
