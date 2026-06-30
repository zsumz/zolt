package com.zolt.build.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.test.runtime.TestRunException;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class TestProfileSummaryFormatterTest {
    @Test
    void printsRankedSlowTestClassAndWorkerSections() {
        Optional<String> summary = TestProfileSummaryFormatter.format(profileJson(), TestProfileSettings.fromCli(
                true,
                Path.of("target/test-profile"),
                2,
                "10ms"));

        assertTrue(summary.isPresent());
        assertEquals(
                """
                Slowest tests:
                  1200 ms com.example.SlowTest#loadsContext
                  75 ms com.example.FastTest#edgeCase

                Slowest classes:
                  1500 ms com.example.SlowTest (1 test)
                  80 ms com.example.FastTest (2 tests)

                Worker balance:
                  1200 ms worker-1 (1 test)
                  80 ms worker-2 (2 tests)""",
                summary.orElseThrow());
    }

    @Test
    void suppressesRowsBelowProfileMinimum() {
        Optional<String> summary = TestProfileSummaryFormatter.format(profileJson(), TestProfileSettings.fromCli(
                true,
                Path.of("target/test-profile"),
                10,
                "1s"));

        assertTrue(summary.isPresent());
        assertTrue(summary.orElseThrow().contains("com.example.SlowTest#loadsContext"));
        assertTrue(summary.orElseThrow().contains("com.example.SlowTest (1 test)"));
        assertTrue(summary.orElseThrow().contains("Worker balance:"));
        assertTrue(!summary.orElseThrow().contains("com.example.FastTest#edgeCase"));
    }

    @Test
    void validatesProfileSummaryControls() {
        TestRunException top = assertThrows(
                TestRunException.class,
                () -> TestProfileSettings.fromCli(true, Path.of("target/test-profile"), 0, null));
        TestRunException min = assertThrows(
                TestRunException.class,
                () -> TestProfileSettings.fromCli(true, Path.of("target/test-profile"), 10, "250"));

        assertTrue(top.getMessage().contains("Invalid --profile-top `0`"));
        assertTrue(min.getMessage().contains("Invalid --profile-min `250`"));
    }

    @Test
    void suppressesSummaryForUnsupportedProfileSchema() {
        String profileJson = profileJson().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2");

        Optional<String> summary = TestProfileSummaryFormatter.format(profileJson, TestProfileSettings.fromCli(
                true,
                Path.of("target/test-profile"),
                10,
                null));

        assertTrue(summary.isEmpty());
    }

    @Test
    void suppressesSummaryForMalformedProfileShape() {
        String profileJson = """
                {
                  "schemaVersion": 1,
                  "containers": [
                    {
                      "className": "com.example.SlowTest",
                      "durationMillis": 1200,
                      "testCount": 1
                    }
                  ]
                }
                """;

        Optional<String> summary = TestProfileSummaryFormatter.format(profileJson, TestProfileSettings.fromCli(
                true,
                Path.of("target/test-profile"),
                10,
                null));

        assertTrue(summary.isEmpty());
    }

    private static String profileJson() {
        return """
                {
                  "schemaVersion": 1,
                  "runner": "zolt-junit-worker",
                  "workerId": "",
                  "summary": {
                    "testsFound": 3,
                    "testsSucceeded": 3,
                    "testsFailed": 0,
                    "testsSkipped": 0,
                    "testsAborted": 0,
                    "durationMillis": 2000
                  },
                  "tests": [
                    {
                      "uniqueId": "[engine:junit-jupiter]/[class:com.example.FastTest]/[method:edgeCase()]",
                      "engineId": "junit-jupiter",
                      "className": "com.example.FastTest",
                      "methodName": "edgeCase",
                      "displayName": "edgeCase()",
                      "status": "passed",
                      "durationMillis": 75,
                      "workerId": "worker-2"
                    },
                    {
                      "uniqueId": "[engine:junit-jupiter]/[class:com.example.FastTest]/[method:tiny()]",
                      "engineId": "junit-jupiter",
                      "className": "com.example.FastTest",
                      "methodName": "tiny",
                      "displayName": "tiny()",
                      "status": "passed",
                      "durationMillis": 5,
                      "workerId": "worker-2"
                    },
                    {
                      "uniqueId": "[engine:junit-jupiter]/[class:com.example.SlowTest]/[method:loadsContext()]",
                      "engineId": "junit-jupiter",
                      "className": "com.example.SlowTest",
                      "methodName": "loadsContext",
                      "displayName": "loadsContext()",
                      "status": "passed",
                      "durationMillis": 1200,
                      "workerId": "worker-1"
                    }
                  ],
                  "containers": [
                    {
                      "uniqueId": "[engine:junit-jupiter]/[class:com.example.FastTest]",
                      "engineId": "junit-jupiter",
                      "className": "com.example.FastTest",
                      "methodName": "",
                      "displayName": "FastTest",
                      "status": "passed",
                      "durationMillis": 80,
                      "workerId": "worker-2",
                      "testCount": 2
                    },
                    {
                      "uniqueId": "[engine:junit-jupiter]/[class:com.example.SlowTest]",
                      "engineId": "junit-jupiter",
                      "className": "com.example.SlowTest",
                      "methodName": "",
                      "displayName": "SlowTest",
                      "status": "passed",
                      "durationMillis": 1500,
                      "workerId": "worker-1",
                      "testCount": 1
                    }
                  ]
                }
                """;
    }
}
