package com.zolt.toml.testsection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.project.ProjectConfig;
import com.zolt.project.TestSuiteSettings;
import com.zolt.toml.ZoltTomlParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ZoltTomlTestSuitesParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesTestSuiteDefinitions() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [test.suites.fast]
                includeClassname = ["*Test", "*Spec"]
                excludeClassname = ["*ContractTest", "*IntegrationSpec"]
                includeTag = ["fast"]
                excludeTag = ["slow", "serial"]
                parallelSafe = true
                maxWorkers = 4
                resourceLocks = { "com.example.DbContractTest" = ["database"], "com.example.KafkaSpec" = ["kafka", "topic"] }

                [test.suites.contract]
                includeClassname = ["*ContractTest"]
                """);

        assertEquals(List.of("contract", "fast"), config.build().testSuites().keySet().stream().toList());
        TestSuiteSettings fast = config.build().testSuites().get("fast");
        assertEquals(List.of("*Test", "*Spec"), fast.includeClassname());
        assertEquals(List.of("*ContractTest", "*IntegrationSpec"), fast.excludeClassname());
        assertEquals(List.of("fast"), fast.includeTag());
        assertEquals(List.of("slow", "serial"), fast.excludeTag());
        assertEquals(true, fast.parallelSafe());
        assertEquals(4, fast.maxWorkers());
        assertEquals(
                Map.of(
                        "com.example.DbContractTest",
                        List.of("database"),
                        "com.example.KafkaSpec",
                        List.of("kafka", "topic")),
                fast.resourceLocks());
        assertEquals(List.of("*ContractTest"), config.build().testSuites().get("contract").includeClassname());
    }
}
