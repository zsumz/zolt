package com.zolt.toml.testsection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.DependencySection;
import com.zolt.project.ProjectConfig;
import com.zolt.project.TestRuntimeSettings;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ZoltTomlTestRuntimeWriterTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void writesTestRuntimeSettingsWhenConfigured() {
        ProjectConfig original = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(BuildSettings.defaults().withTestRuntime(new TestRuntimeSettings(
                        List.of("--add-opens=java.base/java.lang=ALL-UNNAMED"),
                        Map.of("logs.dir", "${project.root}/test-logs"),
                        Map.of("TZ", "America/Chicago", "APP_HOME", "${project.root}"),
                        List.of("failed", "skipped"))));

        String toml = writer.write(original);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[test.runtime]\n"));
        assertTrue(toml.contains("jvmArgs = [\"--add-opens=java.base/java.lang=ALL-UNNAMED\"]"));
        assertTrue(toml.contains("systemProperties = { \"logs.dir\" = \"${project.root}/test-logs\" }"));
        assertTrue(toml.contains("environment = { \"APP_HOME\" = \"${project.root}\", \"TZ\" = \"America/Chicago\" }"));
        assertTrue(toml.contains("events = [\"failed\", \"skipped\"]"));
        assertEquals(original.build().testRuntime(), parsed.build().testRuntime());
    }

    @Test
    void preservesTestRuntimeSettingsWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(BuildSettings.defaults().withTestRuntime(new TestRuntimeSettings(
                        List.of("-Dconfigured=true"),
                        Map.of("logs.dir", "${project.root}/test-logs"),
                        Map.of("TZ", "America/Chicago"),
                        List.of("failed"))));
        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");

        ProjectConfig parsed = parser.parse(writer.write(config));

        assertEquals(config.build().testRuntime(), parsed.build().testRuntime());
    }
}
