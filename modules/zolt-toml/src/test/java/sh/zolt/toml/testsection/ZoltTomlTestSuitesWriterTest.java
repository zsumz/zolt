package sh.zolt.toml.testsection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.BuildSettings;
import sh.zolt.project.DependencySection;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.TestSuiteSettings;
import sh.zolt.toml.ZoltTomlParser;
import sh.zolt.toml.ZoltTomlWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ZoltTomlTestSuitesWriterTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void preservesTestSuitesWhenEditingDependencies() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(BuildSettings.defaults().withTestSuites(Map.of(
                        "fast",
                        new TestSuiteSettings(
                                List.of("*Test", "*Spec"),
                                List.of("*ContractTest"),
                                List.of("fast"),
                                List.of("slow"),
                                true,
                                4,
                                Map.of(
                                        "com.example.DbTest",
                                        List.of("database"),
                                        "com.example.KafkaSpec",
                                        List.of("kafka", "topic"))))));
        config = writer.addDependency(config, DependencySection.TEST, "org.junit.jupiter:junit-jupiter", "5.11.4");

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[test.suites.fast]\n"));
        assertTrue(toml.contains("includeClassname = [\"*Test\", \"*Spec\"]"));
        assertTrue(toml.contains("excludeClassname = [\"*ContractTest\"]"));
        assertTrue(toml.contains("includeTag = [\"fast\"]"));
        assertTrue(toml.contains("excludeTag = [\"slow\"]"));
        assertTrue(toml.contains("parallelSafe = true"));
        assertTrue(toml.contains("maxWorkers = 4"));
        assertTrue(toml.contains("resourceLocks = { \"com.example.DbTest\" = [\"database\"], \"com.example.KafkaSpec\" = [\"kafka\", \"topic\"] }"));
        assertEquals(config.build().testSuites(), parsed.build().testSuites());
    }
}
