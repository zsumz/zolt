package sh.zolt.cli.command.insight;

import sh.zolt.error.ActionableException;
import sh.zolt.explain.gradle.GradleInspectionResult;
import sh.zolt.explain.gradle.GradleProjectInspection;
import sh.zolt.explain.gradle.GradleStaticProjectInspector;
import sh.zolt.explain.MigrationExplainException;
import sh.zolt.explain.verify.GradleProjectCoordinates;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers the Gradle projects to compare and each project's coordinates, by reusing the explain
 * module's static build-file inspector rather than launching Gradle a second time. It produces, for one
 * combined {@code gradle dependencies} invocation:
 * <ul>
 *   <li>the ordered Gradle project paths ({@code ":"} root first, then included projects) to request;
 *   <li>a path-to-coordinates map so {@link sh.zolt.explain.verify.GradleDependencyTreeParser} can give
 *       each parsed block its {@code group:artifact:version} identity and resolve {@code project :x}
 *       edges;
 *   <li>a module-key-to-directory map to enrich the report.
 * </ul>
 *
 * <p>Coordinates come from static inspection, so a project's {@code group}/{@code version} set through
 * {@code allprojects}/{@code subprojects} configuration is recovered here as a best-effort fallback from
 * the root build script; anything the inspector cannot see stays empty rather than being guessed, and
 * such a module simply will not join its Zolt counterpart (surfacing as one-sided, which is honest).
 */
final class GradleProjectDiscovery {
    private static final Pattern GROUP = Pattern.compile("(?m)^\\s*group\\s*=?\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*version\\s*=?\\s*['\"]([^'\"]+)['\"]");

    record GradleProjects(
            List<String> projectPaths,
            Map<String, GradleProjectCoordinates> byPath,
            Map<String, String> directories) {
    }

    private final GradleStaticProjectInspector inspector;

    GradleProjectDiscovery() {
        this(new GradleStaticProjectInspector());
    }

    GradleProjectDiscovery(GradleStaticProjectInspector inspector) {
        this.inspector = inspector;
    }

    GradleProjects discover(Path root) {
        GradleInspectionResult result;
        try {
            result = inspector.inspect(root);
        } catch (MigrationExplainException exception) {
            throw new ActionableException(
                    "Could not read the Gradle build at " + root + ": " + exception.getMessage(),
                    "Run `zolt explain verify` from a Gradle project root (settings.gradle[.kts] or "
                            + "build.gradle[.kts]), or pass --directory <path>.");
        }
        String rootGroup = firstMatch(GROUP, rootBuildScript(root));
        String rootVersion = firstMatch(VERSION, rootBuildScript(root));

        List<String> projectPaths = new ArrayList<>();
        Map<String, GradleProjectCoordinates> byPath = new LinkedHashMap<>();
        Map<String, String> directories = new LinkedHashMap<>();
        for (GradleProjectInspection project : result.projects()) {
            String gradlePath = gradlePath(project.path());
            GradleProjectCoordinates coordinates = new GradleProjectCoordinates(
                    project.group().filter(value -> !value.isBlank()).orElse(rootGroup),
                    project.name(),
                    project.version().filter(value -> !value.isBlank()).orElse(rootVersion));
            projectPaths.add(gradlePath);
            byPath.put(gradlePath, coordinates);
            directories.putIfAbsent(coordinates.moduleKey(), displayPath(project.path()));
        }
        return new GradleProjects(projectPaths, byPath, directories);
    }

    private static String gradlePath(Path relativePath) {
        String path = relativePath == null ? "" : relativePath.toString().replace('\\', '/');
        if (path.isBlank() || path.equals(".")) {
            return ":";
        }
        return ":" + path.replace('/', ':');
    }

    private static String rootBuildScript(Path root) {
        for (String name : List.of("build.gradle", "build.gradle.kts")) {
            Path candidate = root.resolve(name);
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.readString(candidate);
                } catch (IOException ignored) {
                    return "";
                }
            }
        }
        return "";
    }

    private static String firstMatch(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1).strip() : "";
    }

    private static String displayPath(Path path) {
        String rendered = path == null ? "" : path.toString().replace('\\', '/');
        return rendered.isEmpty() ? "." : rendered;
    }
}
