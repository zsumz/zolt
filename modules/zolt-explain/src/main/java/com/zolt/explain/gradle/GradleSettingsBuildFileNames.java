package com.zolt.explain.gradle;

import com.zolt.explain.ExplainSignal;
import com.zolt.explain.ExplainSignals;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GradleSettingsBuildFileNames {
    private static final Pattern BUILD_FILE_NAME_ASSIGNMENT_PATTERN =
            Pattern.compile("\\bproject\\.buildFileName\\s*=\\s*([^\\r\\n;]+)");
    private static final Pattern QUOTED_ASSIGNMENT_PATTERN =
            Pattern.compile("^\\s*(['\"])(.*?)\\1\\s*\\}?\\s*$");
    private static final String PROJECT_NAME_TOKEN = "${project.name}";

    private final Optional<BuildFileNameTemplate> template;
    private final List<String> unresolvedAssignments;

    private GradleSettingsBuildFileNames(
            Optional<BuildFileNameTemplate> template,
            List<String> unresolvedAssignments) {
        this.template = template;
        this.unresolvedAssignments = List.copyOf(unresolvedAssignments);
    }

    static GradleSettingsBuildFileNames parse(String content) {
        Matcher matcher = BUILD_FILE_NAME_ASSIGNMENT_PATTERN.matcher(GradleSourceComments.stripComments(content));
        BuildFileNameTemplate template = null;
        List<String> unresolvedAssignments = new ArrayList<>();
        while (matcher.find()) {
            String assignment = matcher.group(1).strip();
            Optional<BuildFileNameTemplate> parsed = buildFileNameTemplate(assignment);
            if (parsed.isPresent() && template == null) {
                template = parsed.orElseThrow();
            } else if (parsed.isEmpty()) {
                unresolvedAssignments.add("project.buildFileName = " + assignment);
            }
        }
        return new GradleSettingsBuildFileNames(Optional.ofNullable(template), unresolvedAssignments);
    }

    Optional<Path> buildFile(Path directory, String projectName) {
        Optional<String> configuredName = fileNameFor(projectName);
        if (configuredName.isPresent()) {
            Path configured = directory.resolve(configuredName.orElseThrow());
            if (Files.isRegularFile(configured)) {
                return Optional.of(configured);
            }
            return Optional.empty();
        }
        return standardBuildFile(directory);
    }

    Optional<String> fileNameFor(String projectName) {
        return template.map(value -> value.fileNameFor(projectName));
    }

    Optional<ExplainSignal> unresolvedCandidateSignal(
            Path root,
            Path projectDirectory,
            String includedProject) {
        Path candidate = projectNameBuildFileCandidate(projectDirectory);
        if (unresolvedAssignments.isEmpty() || !Files.isRegularFile(candidate)) {
            return Optional.empty();
        }
        return Optional.of(ExplainSignals.GRADLE_PROJECT_BUILD_FILE_NAME_UNRESOLVED.signal(
                projectLabel(root, projectDirectory),
                "Settings buildFileName assignment `" + unresolvedAssignments.getFirst()
                        + "` could not be resolved statically for included Gradle project `"
                        + includedProject + "`; candidate `" + path(root.relativize(candidate))
                        + "` exists but was not guessed as the active build file."));
    }

    private static Optional<BuildFileNameTemplate> buildFileNameTemplate(String assignment) {
        Matcher quoted = QUOTED_ASSIGNMENT_PATTERN.matcher(assignment);
        if (!quoted.matches()) {
            return Optional.empty();
        }
        String value = quoted.group(2).strip();
        if (value.isBlank()) {
            return Optional.empty();
        }
        int token = value.indexOf(PROJECT_NAME_TOKEN);
        if (token >= 0) {
            return Optional.of(new BuildFileNameTemplate(
                    value.substring(0, token),
                    value.substring(token + PROJECT_NAME_TOKEN.length()),
                    true));
        }
        if (value.contains("$") || value.contains("{") || value.contains("}")) {
            return Optional.empty();
        }
        return Optional.of(BuildFileNameTemplate.literal(value));
    }

    private static Optional<Path> standardBuildFile(Path directory) {
        for (Path path : List.of(directory.resolve("build.gradle"), directory.resolve("build.gradle.kts"))) {
            if (Files.isRegularFile(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    private static Path projectNameBuildFileCandidate(Path projectDirectory) {
        return projectDirectory.resolve(projectDirectory.getFileName() + ".gradle.kts");
    }

    private static String projectLabel(Path root, Path projectDirectory) {
        Path relative = root.relativize(projectDirectory);
        return path(relative.toString().isBlank() ? Path.of(".") : relative);
    }

    private static String path(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record BuildFileNameTemplate(String prefix, String suffix, boolean usesProjectName) {
        private static BuildFileNameTemplate literal(String fileName) {
            return new BuildFileNameTemplate(fileName, "", false);
        }

        private String fileNameFor(String projectName) {
            if (!usesProjectName) {
                return prefix;
            }
            return prefix + projectName + suffix;
        }
    }
}
