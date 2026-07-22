package sh.zolt.cli.command.insight;

import sh.zolt.explain.maven.MavenInspectionResult;
import sh.zolt.explain.maven.MavenProjectInspection;
import sh.zolt.explain.maven.MavenStaticProjectInspector;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Best-effort mapping of a Maven reactor's modules to their directories, keyed by
 * {@code group:artifact}, using the existing static POM inspector. The resolved comparison itself is
 * driven by real {@code dependency:tree} output; this only enriches the report with each module's
 * directory. Any inspection failure degrades to an empty map rather than failing the command.
 */
final class MavenModuleDirectories {

    Map<String, String> resolve(Path projectRoot) {
        Map<String, String> directories = new LinkedHashMap<>();
        try {
            MavenInspectionResult result = new MavenStaticProjectInspector().inspect(projectRoot);
            for (MavenProjectInspection project : result.projects()) {
                String key = project.groupId() + ":" + project.artifactId();
                directories.putIfAbsent(key, displayPath(project.path()));
            }
        } catch (RuntimeException ignored) {
            // Directory enrichment is optional; the comparison uses the authoritative Maven tree output.
            return Map.of();
        }
        return directories;
    }

    private static String displayPath(Path path) {
        String rendered = path == null ? "" : path.toString().replace('\\', '/');
        return rendered.isEmpty() ? "." : rendered;
    }
}
