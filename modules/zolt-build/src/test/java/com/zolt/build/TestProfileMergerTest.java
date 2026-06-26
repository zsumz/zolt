package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestProfileMergerTest {
    @TempDir
    private Path tempDir;

    @Test
    void mergesMemberProfileFilesIntoWorkspaceProfile() throws IOException {
        Path api = tempDir.resolve("apps/api/profile.json");
        Path core = tempDir.resolve("modules/core/profile.json");
        writeProfile(api, "com.example.ApiTest", "apps-api", "api", "apps/api", "fast", "1/2");
        writeProfile(core, "com.example.CoreTest", "libs-core", "core", "modules/core", "fast", "2/2");

        TestProfileMerger.mergeProfiles(tempDir, List.of(api, core));

        String merged = Files.readString(tempDir.resolve("profile.json"));
        assertTrue(merged.contains("\"testsFound\": 2"));
        assertTrue(merged.contains("\"testsSucceeded\": 2"));
        assertTrue(merged.contains("\"className\": \"com.example.ApiTest\""));
        assertTrue(merged.contains("\"className\": \"com.example.CoreTest\""));
        assertTrue(merged.contains("\"workerId\": \"apps-api\""));
        assertTrue(merged.contains("\"workerId\": \"libs-core\""));
        assertTrue(merged.contains("\"project\": \"api\""));
        assertTrue(merged.contains("\"project\": \"core\""));
        assertTrue(merged.contains("\"member\": \"apps/api\""));
        assertTrue(merged.contains("\"member\": \"modules/core\""));
        assertTrue(merged.contains("\"suite\": \"fast\""));
        assertTrue(merged.contains("\"shard\": \"1/2\""));
        assertTrue(merged.contains("\"shard\": \"2/2\""));
    }

    private static void writeProfile(
            Path path,
            String className,
            String workerId,
            String project,
            String member,
            String suite,
            String shard) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                {
                  "schemaVersion": 1,
                  "runner": "zolt-junit-worker",
                  "workerId": "%s",
                  "projectRoot": "/workspace",
                  "project": "%s",
                  "member": "%s",
                  "suite": "%s",
                  "shard": "%s",
                  "summary": {
                    "testsFound": 1,
                    "testsSucceeded": 1,
                    "testsFailed": 0,
                    "testsSkipped": 0,
                    "testsAborted": 0,
                    "durationMillis": 100
                  },
                  "tests": [
                    {
                      "uniqueId": "%s#runs",
                      "engineId": "junit-jupiter",
                      "className": "%s",
                      "methodName": "runs",
                      "displayName": "runs()",
                      "status": "passed",
                      "durationMillis": 100,
                      "workerId": "%s",
                      "projectRoot": "/workspace",
                      "project": "%s",
                      "member": "%s",
                      "suite": "%s",
                      "shard": "%s"
                    }
                  ],
                  "containers": [
                    {
                      "uniqueId": "%s",
                      "engineId": "junit-jupiter",
                      "className": "%s",
                      "methodName": "",
                      "displayName": "%s",
                      "status": "passed",
                      "durationMillis": 100,
                      "workerId": "%s",
                      "projectRoot": "/workspace",
                      "project": "%s",
                      "member": "%s",
                      "suite": "%s",
                      "shard": "%s",
                      "testCount": 1
                    }
                  ]
                }
                """.formatted(
                        workerId,
                        project,
                        member,
                        suite,
                        shard,
                        className,
                        className,
                        workerId,
                        project,
                        member,
                        suite,
                        shard,
                        className,
                        className,
                        className,
                        workerId,
                        project,
                        member,
                        suite,
                        shard));
    }
}
