package sh.zolt.explain.emit;

import sh.zolt.project.BuildMetadataSettings;
import sh.zolt.project.BuildSettings;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Maps audited migration roots into the Zolt build settings shape expressible in TOML. */
final class InspectionBuildSettingsMapper {
    private InspectionBuildSettingsMapper() {
    }

    static BuildSettings fromRoots(
            List<String> sourceRoots,
            List<String> testSourceRoots,
            List<String> resourceRoots,
            List<String> testResourceRoots,
            List<String> notes) {
        return fromRoots(sourceRoots, testSourceRoots, List.of(), resourceRoots, testResourceRoots, notes);
    }

    static BuildSettings fromRoots(
            List<String> sourceRoots,
            List<String> testSourceRoots,
            List<String> groovyTestSourceRoots,
            List<String> resourceRoots,
            List<String> testResourceRoots,
            List<String> notes) {
        BuildSettings defaults = BuildSettings.defaults();
        List<String> mainRoots = distinct(sourceRoots);
        List<String> testRoots = distinct(testSourceRoots);
        List<String> groovyTestRoots = distinct(groovyTestSourceRoots);
        String source = mainRoots.isEmpty() ? defaults.source() : mainRoots.getFirst();
        String test = testRoots.isEmpty() ? defaults.test() : testRoots.getFirst();
        if (mainRoots.isEmpty()) {
            notes.add(
                    "No main source root was found by the static audit; [build].source keeps the Zolt"
                            + " convention `src/main/java`. Set it to the real source root before building.");
        }
        return new BuildSettings(
                source,
                mainRoots.isEmpty() ? List.of(source) : mainRoots,
                test,
                defaults.outputRoot(),
                defaults.output(),
                defaults.testOutput(),
                testRoots,
                groovyTestRoots,
                withoutProjectRoot(distinct(resourceRoots), "main", notes),
                withoutProjectRoot(distinct(testResourceRoots), "test", notes),
                BuildMetadataSettings.defaults());
    }

    /**
     * Drops a bare project-root ({@code .} / {@code ./}) resource root and, for each one dropped,
     * adds a review note. Such a root usually comes from a Maven {@code <resource>} that only exists
     * to package one file via {@code <targetPath>}/{@code <includes>}; carrying it verbatim would turn
     * the whole project tree into a Zolt resource root and copy everything into the jar.
     */
    private static List<String> withoutProjectRoot(List<String> roots, String scope, List<String> notes) {
        List<String> kept = new ArrayList<>();
        for (String root : roots) {
            if (isProjectRoot(root)) {
                notes.add(
                        "Maven declared a project-root `" + root + "` resource root in [resources]." + scope
                                + "; it was dropped because it usually only packages a single file via"
                                + " <targetPath>/<includes>, and carrying it live would copy the whole"
                                + " project tree into the jar. Re-add a narrow resource root by hand if needed.");
            } else {
                kept.add(root);
            }
        }
        return List.copyOf(kept);
    }

    private static boolean isProjectRoot(String root) {
        String value = root.strip();
        return value.equals(".") || value.equals("./");
    }

    private static List<String> distinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                distinct.add(value);
            }
        }
        return List.copyOf(distinct);
    }
}
