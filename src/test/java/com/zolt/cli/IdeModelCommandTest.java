package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IdeModelCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void ideModelCheckLockReportsFreshLockfileWithoutDiagnostics() throws IOException {
        try (CliTestRepository repository = CliTestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            Path cacheRoot = tempDir.resolve("cache");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"), Map.of());
            CommandResult resolve = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString());
            String lockfile = Files.readString(projectDir.resolve("zolt.lock"));

            CommandResult result = execute(
                    "ide",
                    "model",
                    "--check-lock",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString(),
                    "--format", "json");

            assertEquals(0, resolve.exitCode());
            assertEquals(0, result.exitCode());
            assertEquals("", result.stderr());
            assertTrue(result.stdout().contains("\"diagnostics\": []"));
            assertEquals(lockfile, Files.readString(projectDir.resolve("zolt.lock")));
        }
    }

    @Test
    void ideModelReportsStaleLockfileWithoutRewritingItByDefault() throws IOException {
        try (CliTestRepository repository = CliTestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            repository.addArtifact("com.example", "extra", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>extra</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            Path cacheRoot = tempDir.resolve("cache");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"), Map.of());
            CommandResult resolve = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString());
            String lockfile = Files.readString(projectDir.resolve("zolt.lock"));
            writeProjectConfig(
                    projectDir,
                    repository.baseUri().toString(),
                    Map.of(
                            "com.example:app", "1.0.0",
                            "com.example:extra", "1.0.0"),
                    Map.of());

            CommandResult result = execute(
                    "ide",
                    "model",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString(),
                    "--format", "json");

            assertEquals(0, resolve.exitCode());
            assertEquals(0, result.exitCode());
            assertEquals("", result.stderr());
            assertTrue(result.stdout().contains("\"code\": \"LOCKFILE_STALE\""));
            assertTrue(result.stdout().contains("zolt.lock is out of date"));
            assertTrue(result.stdout().contains("\"nextStep\": \"Run zolt resolve.\""));
            assertEquals(lockfile, Files.readString(projectDir.resolve("zolt.lock")));
        }
    }

    @Test
    void ideModelPrintsNestedJsonTimingsWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("ide-timings");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "ide", "model",
                "--format", "json",
                "--timings",
                "--timings-format", "json",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"schemaVersion\": 1"));
        assertTrue(result.stderr().contains("\"phase\":\"read ide project config\""));
        assertTrue(result.stderr().contains("\"phase\":\"build ide classpaths\""));
        assertTrue(result.stderr().contains("\"phase\":\"build ide framework model\""));
        assertTrue(result.stderr().contains("\"phase\":\"assemble ide model\""));
        assertTrue(result.stderr().contains("\"phase\":\"ide model export\""));
        assertTrue(result.stderr().contains("\"phase\":\"ide model json\""));
        assertTrue(result.stderr().contains("\"depth\":1"));
        assertTrue(result.stderr().contains("\"testClasspathEntries\""));
    }

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

    @Test
    void ideModelReportsMissingLockfileAsJsonDiagnostic() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "ide",
                "model",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\"code\": \"LOCKFILE_MISSING\""));
        assertTrue(result.stdout().contains("\"nextStep\": \"Run zolt resolve.\""));
        assertTrue(result.stdout().contains("\"compile\": []"));
        assertTrue(result.stdout().contains("\"runtime\": []"));
        assertTrue(result.stdout().contains("\"test\": []"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void ideModelReportsUnreadableLockfileAsJsonDiagnostic() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), "this is not toml");

        CommandResult result = execute(
                "ide",
                "model",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\"code\": \"LOCKFILE_UNREADABLE\""));
        assertTrue(result.stdout().contains("Could not parse zolt.lock"));
        assertTrue(result.stdout().contains("\"nextStep\": \"Run zolt resolve.\""));
        assertTrue(result.stdout().contains("\"compile\": []"));
        assertTrue(result.stdout().contains("\"runtime\": []"));
        assertTrue(result.stdout().contains("\"test\": []"));
    }

    @Test
    void ideModelCheckLockReportsMissingLockfileAsJsonDiagnostic() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "ide",
                "model",
                "--check-lock",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\"code\": \"LOCKFILE_MISSING\""));
        assertTrue(result.stdout().contains("\"nextStep\": \"Run zolt resolve.\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void ideModelCheckLockOfflineReportsUnavailableCacheAsJsonDiagnostic() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(
                projectDir,
                "https://repo.maven.apache.org/maven2",
                Map.of("com.example:missing", "1.0.0"),
                Map.of());
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "ide",
                "model",
                "--check-lock",
                "--offline",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\"code\": \"LOCKFILE_CHECK_UNAVAILABLE\""));
        assertTrue(result.stdout().contains("Offline mode requires cached POM"));
        assertTrue(result.stdout().contains("retry zolt ide model --offline"));
        assertEquals("version = 1\n", Files.readString(projectDir.resolve("zolt.lock")));
    }

    private static void writeProjectConfig(Path projectDir, String repositoryUrl) throws IOException {
        writeProjectConfig(projectDir, repositoryUrl, currentJavaMajorVersion(), Map.of(), Map.of());
    }

    private static void writeProjectConfig(
            Path projectDir,
            String repositoryUrl,
            Map<String, String> dependencies,
            Map<String, String> testDependencies) throws IOException {
        writeProjectConfig(projectDir, repositoryUrl, currentJavaMajorVersion(), dependencies, testDependencies);
    }

    private static void writeProjectConfig(
            Path projectDir,
            String repositoryUrl,
            String javaVersion,
            Map<String, String> dependencies,
            Map<String, String> testDependencies) throws IOException {
        Files.createDirectories(projectDir);
        StringBuilder config = new StringBuilder("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                main = "com.example.Main"

                [repositories]
                test = "%s"

                [dependencies]
                """.formatted(javaVersion, repositoryUrl));
        appendDependencies(config, dependencies);
        config.append("\n[test.dependencies]\n");
        appendDependencies(config, testDependencies);
        config.append("""

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);
        Files.writeString(projectDir.resolve("zolt.toml"), config.toString());
    }

    private static void appendDependencies(StringBuilder config, Map<String, String> dependencies) {
        dependencies.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> config.append('"')
                        .append(entry.getKey())
                        .append("\" = \"")
                        .append(entry.getValue())
                        .append("\"\n"));
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }

    private static String jsonPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
