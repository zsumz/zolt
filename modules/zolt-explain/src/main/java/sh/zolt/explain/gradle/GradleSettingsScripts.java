package sh.zolt.explain.gradle;

import sh.zolt.explain.ExplainSignal;
import sh.zolt.explain.ExplainSignals;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GradleSettingsScripts {
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("\\binclude\\b\\s*(?:\\(([^)]*)\\)|([^\\n]+))");
    private static final Pattern INCLUDE_BUILD_PATTERN = Pattern.compile("\\bincludeBuild\\b\\s*(?:\\(([^)]*)\\)|([^\\n]+))");
    private static final Pattern ROOT_PROJECT_NAME_PATTERN =
            Pattern.compile("\\brootProject\\.name\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern QUOTED_PATTERN = Pattern.compile("['\"]([^'\"]+)['\"]");

    private GradleSettingsScripts() {
    }

    static Optional<String> rootProjectName(String content) {
        Matcher matcher = ROOT_PROJECT_NAME_PATTERN.matcher(GradleSourceComments.stripComments(content));
        if (matcher.find()) {
            String name = matcher.group(1).strip();
            if (!name.isBlank()) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    static List<String> includedProjects(String content) {
        List<String> projects = new ArrayList<>();
        String stripped = GradleSourceComments.stripComments(content);
        List<GradleSignalPatterns.Range> environmentConditionalRanges =
                GradleSignalPatterns.environmentConditionalBlockRanges(stripped);
        Matcher matcher = INCLUDE_PATTERN.matcher(stripped);
        while (matcher.find()) {
            if (GradleSignalPatterns.isInsideAny(matcher.start(), environmentConditionalRanges)) {
                continue;
            }
            String arguments = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            for (String value : quotedValues(arguments)) {
                String path = value.replaceFirst("^:+", "").replace(':', '/');
                if (!path.isBlank()) {
                    projects.add(path);
                }
            }
        }
        return projects.stream().distinct().sorted().toList();
    }

    static List<ExplainSignal> signals(Path root, Path settingsFile, String content) {
        List<ExplainSignal> signals = new ArrayList<>();
        String stripped = GradleSourceComments.stripComments(content);
        Matcher matcher = INCLUDE_BUILD_PATTERN.matcher(stripped);
        while (matcher.find()) {
            String arguments = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            for (String value : quotedValues(arguments)) {
                signals.add(ExplainSignals.GRADLE_INCLUDED_BUILD_DETECTED.signal(
                        ".",
                        "Included Gradle build `" + value + "` is declared in " + root.relativize(settingsFile) + "."));
            }
        }
        Matcher includeMatcher = INCLUDE_PATTERN.matcher(stripped);
        List<GradleSignalPatterns.Range> environmentConditionalRanges =
                GradleSignalPatterns.environmentConditionalBlockRanges(stripped);
        while (includeMatcher.find()) {
            if (!GradleSignalPatterns.isInsideAny(includeMatcher.start(), environmentConditionalRanges)) {
                continue;
            }
            String arguments = includeMatcher.group(1) == null ? includeMatcher.group(2) : includeMatcher.group(1);
            for (String value : quotedValues(arguments)) {
                signals.add(ExplainSignals.GRADLE_SETTINGS_INCLUDE_CONDITIONAL.signal(
                        ".",
                        "Gradle settings conditionally includes project `" + value + "` based on environment-driven logic in " + root.relativize(settingsFile) + "."));
            }
        }
        return signals;
    }

    private static List<String> quotedValues(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Matcher matcher = QUOTED_PATTERN.matcher(input);
        while (matcher.find()) {
            String value = matcher.group(1).trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }
}
