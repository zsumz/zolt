package com.zolt.explain.emit;

import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.BuildSettings;
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
        BuildSettings defaults = BuildSettings.defaults();
        List<String> mainRoots = distinct(sourceRoots);
        List<String> testRoots = distinct(testSourceRoots);
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
                defaults.groovyTestSources(),
                distinct(resourceRoots),
                distinct(testResourceRoots),
                BuildMetadataSettings.defaults());
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
