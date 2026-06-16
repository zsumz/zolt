package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.IdeModelCommandTestSupport.currentJavaMajorVersion;
import static com.zolt.cli.IdeModelCommandTestSupport.jsonPath;
import static com.zolt.cli.IdeModelCommandTestSupport.writeProjectConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IdeModelCommandJsonTest {
    @TempDir
    private Path tempDir;

    @Test
    void ideModelPrintsDeterministicJsonFromProjectAndLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:app"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "com/example/app/1.0.0/app-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:test-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "com/example/test-lib/1.0.0/test-lib-1.0.0.jar"
                dependencies = []
                """);

        CommandResult result = execute(
                "ide",
                "model",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "--format", "json");

        Path root = projectDir.toAbsolutePath().normalize();
        Path appJar = cacheRoot.toAbsolutePath().normalize().resolve("com/example/app/1.0.0/app-1.0.0.jar");
        Path testJar = cacheRoot.toAbsolutePath().normalize().resolve("com/example/test-lib/1.0.0/test-lib-1.0.0.jar");
        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertEquals("""
                {
                  "schemaVersion": 1,
                  "project": {
                    "name": "demo",
                    "group": "com.example",
                    "version": "0.1.0",
                    "mainClass": "com.example.Main"
                  },
                  "java": {
                    "version": "%s",
                    "detectedVersion": null,
                    "javaHome": null
                  },
                  "compiler": {
                    "release": null,
                    "effectiveRelease": "%s",
                    "encoding": null,
                    "args": [],
                    "testArgs": [],
                    "generatedSources": "%s",
                    "generatedTestSources": "%s"
                  },
                  "testRuntime": {
                    "jvmArgs": [],
                    "systemProperties": {},
                    "environment": {},
                    "events": []
                  },
                  "package": {
                    "mode": "thin",
                    "sources": false,
                    "javadoc": false,
                    "tests": false,
                    "mainJar": "%s",
                    "sourcesJar": null,
                    "javadocJar": null,
                    "testsJar": null,
                    "metadata": {
                      "name": null,
                      "description": null,
                      "url": null,
                      "license": null,
                      "developers": [],
                      "scm": null,
                      "issues": null
                    },
                    "manifestAttributes": {}
                  },
                  "paths": {
                    "root": "%s",
                    "config": "%s",
                    "lockfile": "%s"
                  },
                  "sourceRoots": [
                    {
                      "id": "main-java",
                      "kind": "main",
                      "language": "java",
                      "path": "%s",
                      "generated": false
                    },
                    {
                      "id": "main-generated-java",
                      "kind": "main",
                      "language": "java",
                      "path": "%s",
                      "generated": true
                    },
                    {
                      "id": "test-java-1",
                      "kind": "test",
                      "language": "java",
                      "path": "%s",
                      "generated": false
                    },
                    {
                      "id": "test-generated-java",
                      "kind": "test",
                      "language": "java",
                      "path": "%s",
                      "generated": true
                    }
                  ],
                  "generatedSources": [],
                  "resourceRoots": [
                    {
                      "id": "main-resources",
                      "kind": "main",
                      "path": "%s"
                    },
                    {
                      "id": "test-resources",
                      "kind": "test",
                      "path": "%s"
                    }
                  ],
                  "outputs": {
                    "mainClasses": "%s",
                    "testClasses": "%s",
                    "package": "%s"
                  },
                  "dependencies": {
                    "versionAliases": {},
                    "api": [],
                    "implementation": [],
                    "runtime": [],
                    "provided": [],
                    "dev": [],
                    "test": [],
                    "annotationProcessors": [],
                    "testAnnotationProcessors": []
                  },
                  "classpaths": {
                    "compile": [
                      "%s"
                    ],
                    "runtime": [
                      "%s",
                      "%s"
                    ],
                    "test": [
                      "%s",
                      "%s",
                      "%s",
                      "%s"
                    ],
                    "processor": [],
                    "testProcessor": [],
                    "quarkusDeployment": []
                  },
                  "frameworks": {
                    "quarkus": {
                      "enabled": false,
                      "packageMode": null,
                      "augmentationStatus": "disabled",
                      "inputFingerprint": null,
                      "recordedInputFingerprint": null,
                      "augmentationMetadata": null,
                      "augmentationDirectory": null,
                      "packageDirectory": null,
                      "runnerJar": null,
                      "generatedBytecodeJar": null,
                      "transformedBytecodeJar": null,
                      "deploymentClasspath": []
                    }
                  },
                  "diagnostics": [
                    {
                      "severity": "error",
                      "code": "LOCKFILE_STALE",
                      "message": "zolt.lock is out of date. Run `zolt resolve` to refresh it, then retry `zolt resolve --locked`.",
                      "path": "%s",
                      "nextStep": "Run zolt resolve."
                    }
                  ]
                }
                """.formatted(
                currentJavaMajorVersion(),
                currentJavaMajorVersion(),
                jsonPath(root.resolve("target/generated/sources/annotations")),
                jsonPath(root.resolve("target/generated/test-sources/annotations")),
                jsonPath(root.resolve("target/demo-0.1.0.jar")),
                jsonPath(root),
                jsonPath(root.resolve("zolt.toml")),
                jsonPath(root.resolve("zolt.lock")),
                jsonPath(root.resolve("src/main/java")),
                jsonPath(root.resolve("target/generated/sources/annotations")),
                jsonPath(root.resolve("src/test/java")),
                jsonPath(root.resolve("target/generated/test-sources/annotations")),
                jsonPath(root.resolve("src/main/resources")),
                jsonPath(root.resolve("src/test/resources")),
                jsonPath(root.resolve("target/classes")),
                jsonPath(root.resolve("target/test-classes")),
                jsonPath(root.resolve("target/demo-0.1.0.jar")),
                jsonPath(appJar),
                jsonPath(root.resolve("target/classes")),
                jsonPath(appJar),
                jsonPath(root.resolve("target/classes")),
                jsonPath(root.resolve("target/test-classes")),
                jsonPath(appJar),
                jsonPath(testJar),
                jsonPath(root.resolve("zolt.lock"))), result.stdout());
    }
}
