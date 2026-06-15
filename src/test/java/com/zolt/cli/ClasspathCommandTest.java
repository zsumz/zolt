package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClasspathCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void classpathRejectsStaleExistingLockfileWhenProjectConfigExists() throws IOException {
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
            Path projectDir = tempDir.resolve("classpath-stale-lock");
            Path cacheRoot = tempDir.resolve("classpath-stale-lock-cache");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"));
            CommandResult resolve = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString());
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of(
                    "com.example:app", "1.0.0",
                    "com.example:extra", "1.0.0"));

            CommandResult result = execute(
                    "classpath",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString(),
                    "compile");

            assertEquals(0, resolve.exitCode());
            assertEquals(1, result.exitCode());
            assertTrue(result.stderr().contains("zolt.lock is out of date"));
            assertTrue(result.stderr().contains("Run `zolt resolve` to refresh it"));
            assertEquals("", result.stdout());
        }
    }

    @Test
    void classpathPrintsRequestedClasspathFromLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:compile-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "com/example/compile-lib/1.0.0/compile-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:test-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "com/example/test-lib/1.0.0/test-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:processor-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "processor"
                direct = true
                jar = "com/example/processor-lib/1.0.0/processor-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:test-processor-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "test-processor"
                direct = true
                jar = "com/example/test-processor-lib/1.0.0/test-processor-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "io.quarkus:quarkus-rest-deployment"
                version = "3.33.0"
                source = "maven-central"
                scope = "quarkus-deployment"
                direct = false
                jar = "io/quarkus/quarkus-rest-deployment/3.33.0/quarkus-rest-deployment-3.33.0.jar"
                dependencies = []
                """);

        Path compileJar = cacheRoot.resolve("com/example/compile-lib/1.0.0/compile-lib-1.0.0.jar");
        Path runtimeJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Path testJar = cacheRoot.resolve("com/example/test-lib/1.0.0/test-lib-1.0.0.jar");
        Path processorJar = cacheRoot.resolve("com/example/processor-lib/1.0.0/processor-lib-1.0.0.jar");
        Path testProcessorJar = cacheRoot.resolve(
                "com/example/test-processor-lib/1.0.0/test-processor-lib-1.0.0.jar");
        Path quarkusDeploymentJar = cacheRoot.resolve(
                "io/quarkus/quarkus-rest-deployment/3.33.0/quarkus-rest-deployment-3.33.0.jar");

        CommandResult compile = execute(
                "classpath",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "compile");
        CommandResult runtime = execute(
                "classpath",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "runtime");
        CommandResult test = execute(
                "classpath",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "test");
        CommandResult processor = execute(
                "classpath",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "processor");
        CommandResult testProcessor = execute(
                "classpath",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "test-processor");
        CommandResult quarkusDeployment = execute(
                "classpath",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "quarkus-deployment");

        assertEquals(0, compile.exitCode());
        assertEquals(compileJar + System.lineSeparator(), compile.stdout());
        assertEquals(0, runtime.exitCode());
        assertEquals(
                compileJar + File.pathSeparator + runtimeJar + System.lineSeparator(),
                runtime.stdout());
        assertEquals(0, test.exitCode());
        assertEquals(
                compileJar + File.pathSeparator + runtimeJar + File.pathSeparator + testJar + System.lineSeparator(),
                test.stdout());
        assertEquals(0, processor.exitCode());
        assertEquals(processorJar + System.lineSeparator(), processor.stdout());
        assertEquals(0, testProcessor.exitCode());
        assertEquals(testProcessorJar + System.lineSeparator(), testProcessor.stdout());
        assertEquals(0, quarkusDeployment.exitCode());
        assertEquals(quarkusDeploymentJar + System.lineSeparator(), quarkusDeployment.stdout());
    }

    @Test
    void classpathFailsWhenCachedJarDoesNotMatchLockfileHash() throws IOException {
        Path projectDir = tempDir.resolve("demo-corrupted-cache");
        Path cacheRoot = tempDir.resolve("cache-corrupted");
        Files.createDirectories(projectDir);
        Path jar = cacheRoot.resolve("com/example/compile-lib/1.0.0/compile-lib-1.0.0.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "corrupted jar bytes");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:compile-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "com/example/compile-lib/1.0.0/compile-lib-1.0.0.jar"
                jarSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
                dependencies = []
                """);

        CommandResult result = execute(
                "classpath",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "compile");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains(
                "Cached jar integrity check failed for com.example:compile-lib:1.0.0"));
        assertTrue(result.stderr().contains(
                "Expected 0000000000000000000000000000000000000000000000000000000000000000"));
        assertTrue(result.stderr().contains("Remove the cache entry or run `zolt resolve`"));
    }

    @Test
    void classpathRejectsUnknownKindWithSupportedKinds() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute("classpath", "--cwd", projectDir.toString(), "processor-test");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Unknown classpath kind `processor-test`"));
        assertTrue(result.stderr().contains("compile, runtime, test, processor, test-processor, quarkus-deployment, or audit"));
    }

    @Test
    void classpathAuditPrintsLanePolicyAndResolvedPackages() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "jakarta.servlet:jakarta.servlet-api"
                version = "6.1.0"
                source = "maven-central"
                scope = "provided"
                direct = true
                jar = "jakarta/servlet/jakarta.servlet-api/6.1.0/jakarta.servlet-api-6.1.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:devtools"
                version = "1.0.0"
                source = "maven-central"
                scope = "dev"
                direct = true
                jar = "com/example/devtools/1.0.0/devtools-1.0.0.jar"
                dependencies = []
                """);

        CommandResult result = execute("classpath", "--cwd", projectDir.toString(), "audit");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Classpath lane audit"));
        assertTrue(result.stdout().contains(
                "provided            yes     no      no   no        no             no           no            no              provided-container"));
        assertTrue(result.stdout().contains(
                "- com.example:devtools:1.0.0 [dev] lanes=runtime,test package=development-only"));
        assertTrue(result.stdout().contains(
                "- jakarta.servlet:jakarta.servlet-api:6.1.0 [provided] lanes=compile package=provided-container"));
        assertEquals("", result.stderr());
    }

    @Test
    void classpathAuditPrintsJsonForTooling() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "jakarta.servlet:jakarta.servlet-api"
                version = "6.1.0"
                source = "maven-central"
                scope = "provided"
                direct = true
                jar = "jakarta/servlet/jakarta.servlet-api/6.1.0/jakarta.servlet-api-6.1.0.jar"
                dependencies = []
                """);

        CommandResult result = execute("classpath", "--cwd", projectDir.toString(), "audit", "--format", "json");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"command\": \"classpath audit\""));
        assertTrue(result.stdout().contains("\"scope\": \"provided\""));
        assertTrue(result.stdout().contains("\"lanes\": [\"compile\"]"));
        assertTrue(result.stdout().contains("\"disposition\": \"provided-container\""));
        assertEquals("", result.stderr());
    }

    @Test
    void classpathJsonFormatIsOnlyForAudit() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute("classpath", "--cwd", projectDir.toString(), "compile", "--format", "json");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Use `zolt classpath audit --format json`."));
    }

    @Test
    void classpathReportsMissingLockfileCleanly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);

        CommandResult result = execute("classpath", "--cwd", projectDir.toString(), "runtime");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.lock"));
    }

    private static void writeProjectConfig(
            Path projectDir,
            String repositoryUrl,
            Map<String, String> dependencies) throws IOException {
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
                """.formatted(currentJavaMajorVersion(), repositoryUrl));
        dependencies.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> config.append('"')
                        .append(entry.getKey())
                        .append("\" = \"")
                        .append(entry.getValue())
                        .append("\"\n"));
        config.append("""

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);
        Files.writeString(projectDir.resolve("zolt.toml"), config.toString());
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }
}
