package sh.zolt.explain.maven;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure classification and message helpers used by {@link MavenStaticProjectInspector} when deriving
 * signals from an inspection. Kept apart so the inspector stays focused on orchestrating the audit.
 */
final class MavenSignalRules {
    private MavenSignalRules() {
    }

    static String reactorMessage(int members, List<String> profileModules) {
        if (profileModules.isEmpty()) {
            return "Multi-module reactor with " + members + " module(s); `zolt explain --emit-toml`"
                    + " emits a Zolt workspace with a root [workspace] plus one member draft per module.";
        }
        return "Multi-module reactor with " + members + " top-level module(s) plus "
                + profileModules.size()
                + " profile-declared module(s) omitted from default workspace coverage: "
                + String.join(", ", profileModules)
                + "; `zolt explain --emit-toml` emits a Zolt workspace with a root [workspace]"
                + " plus one member draft per top-level module.";
    }

    static boolean knownPlugin(String coordinate) {
        return coordinate.contains(":maven-compiler-plugin")
                || coordinate.contains(":maven-surefire-plugin")
                || coordinate.contains(":maven-failsafe-plugin")
                || coordinate.contains(":spring-boot-maven-plugin");
    }

    static String phaseSuffix(MavenPluginInspection plugin) {
        if (plugin.phases().isEmpty()) {
            return "";
        }
        return " in effective lifecycle phase(s) " + plugin.phases();
    }

    static boolean unsupportedLanguagePlugin(String coordinate) {
        String lower = coordinate.toLowerCase();
        return lower.contains(":kotlin-maven-plugin")
                || lower.contains(":scala-maven-plugin")
                || lower.contains(":android-maven-plugin");
    }

    static boolean unsupportedFrameworkNativePlugin(MavenPluginInspection plugin) {
        String lower = plugin.coordinate().toLowerCase();
        if (lower.contains(":native-maven-plugin") || lower.contains(":micronaut-maven-plugin")) {
            return true;
        }
        if (!lower.contains(":spring-boot-maven-plugin")) {
            return false;
        }
        return plugin.goals().stream()
                .map(String::toLowerCase)
                .anyMatch(goal -> goal.contains("aot") || goal.contains("build-image") || goal.contains("native"));
    }

    static List<MavenDependencyInspection> concat(
            List<MavenDependencyInspection> first,
            List<MavenDependencyInspection> second) {
        List<MavenDependencyInspection> combined = new ArrayList<>(first);
        combined.addAll(second);
        return combined;
    }
}
