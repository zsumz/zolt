package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.ProjectConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ZoltTomlTestRuntimeParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesTestRuntimeSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "test-runtime"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [test.runtime]
                jvmArgs = ["--add-opens=java.base/java.lang=ALL-UNNAMED"]
                systemProperties = { "logs.dir" = "${project.root}/test-logs" }
                environment = { TZ = "America/Chicago", APP_HOME = "${project.root}" }
                events = ["failed", "skipped"]
                """);

        assertEquals(
                List.of("--add-opens=java.base/java.lang=ALL-UNNAMED"),
                config.build().testRuntime().jvmArgs());
        assertEquals(
                Map.of("logs.dir", "${project.root}/test-logs"),
                config.build().testRuntime().systemProperties());
        assertEquals(
                Map.of("APP_HOME", "${project.root}", "TZ", "America/Chicago"),
                config.build().testRuntime().environment());
        assertEquals(List.of("failed", "skipped"), config.build().testRuntime().events());
    }

    @Test
    void rejectsUnknownTestRuntimeEvents() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "bad-test-runtime"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [test.runtime]
                events = ["verbose"]
                """));

        assertTrue(exception.getMessage().contains("Unsupported test runtime event `verbose`"));
    }
}
