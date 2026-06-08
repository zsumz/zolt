package com.zolt.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class ZoltCliTest {
    @TempDir
    private Path tempDir;

    @Test
    void versionPrintsZoltVersion() {
        CommandResult result = execute("--version");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("zolt 0.1.0-SNAPSHOT"));
    }

    @Test
    void helpListsMvpCommands() {
        CommandResult result = execute("help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("The modern Java build toolkit."));
        assertTrue(result.stdout().contains("init"));
        assertTrue(result.stdout().contains("resolve"));
        assertTrue(result.stdout().contains("build"));
        assertTrue(result.stdout().contains("doctor"));
    }

    @Test
    void registersMvpCommandSurface() {
        Set<String> subcommands = ZoltCli.newCommandLine().getSubcommands().keySet();

        assertTrue(subcommands.containsAll(Set.of(
                "help",
                "init",
                "add",
                "remove",
                "platform",
                "resolve",
                "tree",
                "why",
                "conflicts",
                "classpath",
                "ide",
                "quarkus",
                "build",
                "run",
                "test",
                "package",
                "run-package",
                "native",
                "native-smoke",
                "release-archive",
                "release-verify",
                "self-check",
                "self-parity",
                "clean",
                "doctor")));
    }

    @Test
    void packageReportsConfigErrorsCleanly() {
        CommandResult result = execute("package", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.toml"));
    }

    @Test
    void initCreatesProjectAndPrintsNextCommand() {
        CommandResult result = execute("init", "--cwd", tempDir.toString(), "hello");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Created Zolt project at"));
        assertTrue(result.stdout().contains("Next: cd hello"));
        assertTrue(Files.exists(tempDir.resolve("hello/zolt.toml")));
    }

    @Test
    void resolveReportsConfigErrorsCleanly() {
        CommandResult result = execute("resolve", "--cwd", tempDir.toString(), "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.toml"));
    }

    @Test
    void resolveReadsConfigWritesLockfileAndPrintsSummary() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            Files.createDirectories(projectDir);
            Files.writeString(projectDir.resolve("zolt.toml"), """
                    [project]
                    name = "demo"
                    version = "0.1.0"
                    group = "com.example"
                    java = "21"
                    main = "com.example.Main"

                    [repositories]
                    test = "%s"

                    [dependencies]
                    "com.example:app" = "1.0.0"
                    """.formatted(repository.baseUri()));

            CommandResult result = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Resolved 1 packages"));
            assertTrue(result.stdout().contains("Downloaded 2 artifacts"));
            assertTrue(result.stdout().contains("Conflicts 0"));
            assertTrue(result.stdout().contains("Wrote " + projectDir.resolve("zolt.lock")));
            assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
        }
    }

    @Test
    void resolveLockedVerifiesExistingLockfile() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            Files.createDirectories(projectDir);
            Files.writeString(projectDir.resolve("zolt.toml"), """
                    [project]
                    name = "demo"
                    version = "0.1.0"
                    group = "com.example"
                    java = "21"
                    main = "com.example.Main"

                    [repositories]
                    test = "%s"

                    [dependencies]
                    "com.example:app" = "1.0.0"
                    """.formatted(repository.baseUri()));
            CommandResult unlocked = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            CommandResult locked = execute(
                    "resolve",
                    "--locked",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            assertEquals(0, unlocked.exitCode());
            assertEquals(0, locked.exitCode());
            assertTrue(locked.stdout().contains("Verified " + projectDir.resolve("zolt.lock")));
        }
    }

    @Test
    void resolveLockedReportsMissingLockfileClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "resolve",
                "--locked",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Locked resolve requires zolt.lock"));
        assertTrue(result.stderr().contains("Run `zolt resolve` to create it"));
    }

    @Test
    void resolveOfflineReportsMissingCachedArtifactClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(
                projectDir,
                "https://repo.maven.apache.org/maven2",
                Map.of("com.example:missing", "1.0.0"),
                Map.of());

        CommandResult result = execute(
                "resolve",
                "--offline",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Offline mode requires cached POM"));
        assertTrue(result.stderr().contains("Run the command without --offline"));
    }

    @Test
    void addAddsCompileDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "com.google.guava:guava:33.4.0-jre");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Added dependency com.google.guava:guava:33.4.0-jre to [dependencies]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("\"com.google.guava:guava\" = \"33.4.0-jre\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsTestDependencyWithoutDuplicatingExistingEntry() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult first = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "test",
                "org.junit.jupiter:junit-jupiter:5.11.4");
        CommandResult second = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "test",
                "org.junit.jupiter:junit-jupiter:5.11.4");

        assertEquals(0, first.exitCode());
        assertEquals(0, second.exitCode());
        assertTrue(first.stdout().contains("Added dependency org.junit.jupiter:junit-jupiter:5.11.4 to [test.dependencies]"));
        assertTrue(second.stdout().contains("already exists in [test.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertEquals(1, occurrences(config, "\"org.junit.jupiter:junit-jupiter\" = \"5.11.4\""));
    }

    @Test
    void addAddsManagedDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "com.example:legacy-api");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency com.example:legacy-api with a platform-managed version to [dependencies]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("\"com.example:legacy-api\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsApiDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                "central" = "https://repo.maven.apache.org/maven2"

                [platforms]

                [dependencies]
                "com.example:contract" = "1.0.0"

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "api",
                "com.example:contract:2.0.0");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Updated dependency com.example:contract from 1.0.0 to 2.0.0 in [api.dependencies]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[api.dependencies]\n\"com.example:contract\" = \"2.0.0\""));
        assertFalse(config.contains("[dependencies]\n\"com.example:contract\" = \"1.0.0\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsManagedApiDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "api",
                "com.example:contract");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency com.example:contract with a platform-managed version to [api.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[api.dependencies]\n\"com.example:contract\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsRuntimeDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "runtime",
                "com.h2database:h2");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency com.h2database:h2 with a platform-managed version to [runtime.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[runtime.dependencies]\n\"com.h2database:h2\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsProvidedDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "provided",
                "jakarta.servlet:jakarta.servlet-api:6.1.0");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency jakarta.servlet:jakarta.servlet-api:6.1.0 to [provided.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[provided.dependencies]\n\"jakarta.servlet:jakarta.servlet-api\" = \"6.1.0\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsDevDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "dev",
                "org.springframework.boot:spring-boot-devtools");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency org.springframework.boot:spring-boot-devtools with a platform-managed version to [dev.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[dev.dependencies]\n\"org.springframework.boot:spring-boot-devtools\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsProcessorDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "processor",
                "org.mapstruct:mapstruct-processor:1.6.3");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency org.mapstruct:mapstruct-processor:1.6.3 to [annotationProcessors]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[annotationProcessors]"));
        assertTrue(config.contains("\"org.mapstruct:mapstruct-processor\" = \"1.6.3\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsManagedTestProcessorDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "test-processor",
                "io.micronaut:micronaut-inject-java");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency io.micronaut:micronaut-inject-java with a platform-managed version to [test.annotationProcessors]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[test.annotationProcessors]"));
        assertTrue(config.contains("\"io.micronaut:micronaut-inject-java\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addRejectsManagedDependencyWithExplicitVersion() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--managed",
                "com.example:legacy-api:1.0.0");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains(
                "Managed dependency coordinate must not include a version. Use `group:artifact`."));
    }

    @Test
    void addRejectsUnknownDependencySectionWithSupportedSections() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "compile-only",
                "com.example:tool:1.0.0");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Unexpected dependency section `compile-only`"));
        assertTrue(result.stderr().contains("zolt add api group:artifact"));
        assertTrue(result.stderr().contains("zolt add runtime group:artifact"));
        assertTrue(result.stderr().contains("zolt add provided group:artifact"));
        assertTrue(result.stderr().contains("zolt add dev group:artifact"));
        assertTrue(result.stderr().contains("zolt add processor group:artifact"));
        assertTrue(result.stderr().contains("zolt add test-processor group:artifact"));
    }

    @Test
    void addRefreshesLockfileByDefault() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            writeProjectConfig(projectDir, repository.baseUri().toString());

            CommandResult result = execute(
                    "add",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString(),
                    "com.example:app:1.0.0");

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Added dependency com.example:app:1.0.0 to [dependencies]"));
            assertTrue(result.stdout().contains("Resolved 1 packages"));
            assertTrue(result.stdout().contains("Downloaded 2 artifacts"));
            assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
        }
    }

    @Test
    void removeDeletesDependencyAndPrunesUnusedTransitivesFromLockfile() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                        <dependency>
                          <groupId>com.example</groupId>
                          <artifactId>lib</artifactId>
                          <version>1.0.0</version>
                        </dependency>
                      </dependencies>
                    </project>
                    """);
            repository.addArtifact("com.example", "lib", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>lib</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"), Map.of());

            CommandResult resolve = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());
            String initialLockfile = Files.readString(projectDir.resolve("zolt.lock"));

            CommandResult remove = execute(
                    "remove",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString(),
                    "com.example:app");

            assertEquals(0, resolve.exitCode());
            assertTrue(initialLockfile.contains("com.example:app"));
            assertTrue(initialLockfile.contains("com.example:lib"));
            assertEquals(0, remove.exitCode());
            assertTrue(remove.stdout().contains("Removed dependency com.example:app from [dependencies]"));
            assertTrue(remove.stdout().contains("Resolved 0 packages"));
            String config = Files.readString(projectDir.resolve("zolt.toml"));
            String updatedLockfile = Files.readString(projectDir.resolve("zolt.lock"));
            assertFalse(config.contains("\"com.example:app\" = \"1.0.0\""));
            assertFalse(updatedLockfile.contains("com.example:app"));
            assertFalse(updatedLockfile.contains("com.example:lib"));
        }
    }

    @Test
    void removeDeletesDependencyFromRequestedSectionOnly() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "tool", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>tool</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            writeProjectConfig(
                    projectDir,
                    repository.baseUri().toString(),
                    Map.of("com.example:tool", "1.0.0"),
                    Map.of("com.example:tool", "1.0.0"));

            CommandResult result = execute(
                    "remove",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString(),
                    "test",
                    "com.example:tool");

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Removed dependency com.example:tool from [test.dependencies]"));
            String config = Files.readString(projectDir.resolve("zolt.toml"));
            assertEquals(1, occurrences(config, "\"com.example:tool\" = \"1.0.0\""));
            assertTrue(config.indexOf("[dependencies]") < config.indexOf("\"com.example:tool\" = \"1.0.0\""));
            assertTrue(config.indexOf("\"com.example:tool\" = \"1.0.0\"") < config.indexOf("[test.dependencies]"));
        }
    }

    @Test
    void removeMissingDependencyPrintsFriendlyNoOpMessage() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "remove",
                "--cwd", projectDir.toString(),
                "com.example:missing");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Dependency com.example:missing is not present in [dependencies]; nothing to remove."));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void removeWithoutSectionDoesNotDeleteApiDependency() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                "central" = "https://repo.maven.apache.org/maven2"

                [platforms]

                [api.dependencies]
                "com.example:contract" = "1.0.0"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);

        CommandResult result = execute(
                "remove",
                "--cwd", projectDir.toString(),
                "com.example:contract");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Dependency com.example:contract is not present in [dependencies]; nothing to remove."));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[api.dependencies]\n\"com.example:contract\" = \"1.0.0\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void removeDeletesApiDependency() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                "central" = "https://repo.maven.apache.org/maven2"

                [platforms]

                [api.dependencies]
                "com.example:contract" = "1.0.0"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);

        CommandResult result = execute(
                "remove",
                "--cwd", projectDir.toString(),
                "api",
                "com.example:contract");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Removed dependency com.example:contract from [api.dependencies]"));
        assertTrue(result.stdout().contains("Resolved 0 packages"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertFalse(config.contains("\"com.example:contract\" = \"1.0.0\""));
    }

    @Test
    void removeDeletesProcessorDependencyFromRequestedSection() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "processor", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>processor</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            Files.createDirectories(projectDir);
            Files.writeString(projectDir.resolve("zolt.toml"), """
                    [project]
                    name = "demo"
                    version = "0.1.0"
                    group = "com.example"
                    java = "21"

                    [repositories]
                    "local" = "%s"

                    [platforms]

                    [dependencies]
                    "com.example:processor" = "1.0.0"

                    [test.dependencies]

                    [annotationProcessors]
                    "com.example:processor" = "1.0.0"

                    [build]
                    source = "src/main/java"
                    test = "src/test/java"
                    output = "target/classes"
                    testOutput = "target/test-classes"
                    """.formatted(repository.baseUri()));

            CommandResult result = execute(
                    "remove",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString(),
                    "processor",
                    "com.example:processor");

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Removed dependency com.example:processor from [annotationProcessors]"));
            assertTrue(result.stdout().contains("Resolved 1 packages"));
            String config = Files.readString(projectDir.resolve("zolt.toml"));
            assertTrue(config.contains("[dependencies]\n\"com.example:processor\" = \"1.0.0\""));
            assertFalse(config.contains("[annotationProcessors]\n\"com.example:processor\" = \"1.0.0\""));
        }
    }

    @Test
    void platformAddWritesPlatformWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "platform",
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "com.example:enterprise-platform:2026.1.0");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added platform com.example:enterprise-platform:2026.1.0 to [platforms]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("\"com.example:enterprise-platform\" = \"2026.1.0\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void platformAddRefreshesLockfileByDefault() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "enterprise-platform", "2026.1.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>enterprise-platform</artifactId>
                      <version>2026.1.0</version>
                      <dependencyManagement>
                        <dependencies>
                          <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>legacy-api</artifactId>
                            <version>1.5.0</version>
                          </dependency>
                        </dependencies>
                      </dependencyManagement>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            writeProjectConfig(projectDir, repository.baseUri().toString());

            CommandResult result = execute(
                    "platform",
                    "add",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString(),
                    "com.example:enterprise-platform:2026.1.0");

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Added platform com.example:enterprise-platform:2026.1.0 to [platforms]"));
            assertTrue(result.stdout().contains("Resolved 0 packages"));
            assertTrue(result.stdout().contains("Downloaded 1 artifacts"));
            assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
        }
    }

    @Test
    void platformRemoveDeletesPlatformAndRefreshesLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        CommandResult add = execute(
                "platform",
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "com.example:enterprise-platform:2026.1.0");

        CommandResult remove = execute(
                "platform",
                "remove",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "com.example:enterprise-platform");

        assertEquals(0, add.exitCode());
        assertEquals(0, remove.exitCode());
        assertTrue(remove.stdout().contains("Removed platform com.example:enterprise-platform from [platforms]"));
        assertTrue(remove.stdout().contains("Resolved 0 packages"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertFalse(config.contains("\"com.example:enterprise-platform\""));
    }

    @Test
    void platformRemoveRejectsVersionedCoordinate() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "platform",
                "remove",
                "--cwd", projectDir.toString(),
                "com.example:enterprise-platform:2026.1.0");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains(
                "Platform remove coordinate must not include a version. Use `group:artifact`."));
    }

    @Test
    void treePrintsDependencyTreeFromProjectLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:app"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                dependencies = ["com.example:lib:1.0.0"]

                [[package]]
                id = "com.example:lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = false
                dependencies = []
                """);

        CommandResult result = execute("tree", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals("""
                com.example:demo:0.1.0
                \\- com.example:app:1.0.0
                   \\- com.example:lib:1.0.0
                """, result.stdout());
    }

    @Test
    void treeReportsMissingLockfileCleanly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute("tree", "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.lock"));
    }

    @Test
    void whyPrintsPathFromProjectRootToPackage() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:app"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                dependencies = ["com.example:lib:1.0.0"]

                [[package]]
                id = "com.example:lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = false
                dependencies = []
                """);

        CommandResult result = execute("why", "--cwd", projectDir.toString(), "com.example:lib");

        assertEquals(0, result.exitCode());
        assertEquals("""
                com.example:demo:0.1.0
                \\- com.example:app:1.0.0
                   \\- com.example:lib:1.0.0
                """, result.stdout());
    }

    @Test
    void whyReportsMissingPackageClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute("why", "--cwd", projectDir.toString(), "com.example:missing");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Package com.example:missing is not present in zolt.lock"));
    }

    @Test
    void conflictsPrintsConflictSummaryFromLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[conflict]]
                id = "org.slf4j:slf4j-api"
                selected = "2.0.16"
                requested = ["1.7.36", "2.0.16"]
                reason = "direct dependency wins"
                """);

        CommandResult result = execute("conflicts", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals("""
                Dependency conflicts:
                - org.slf4j:slf4j-api
                  selected: 2.0.16
                  requested: 1.7.36, 2.0.16
                  reason: direct dependency wins
                """, result.stdout());
    }

    @Test
    void conflictsExitsSuccessfullyWhenNoConflictsExist() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute("conflicts", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals("No dependency conflicts found.\n", result.stdout());
    }

    @Test
    void formattedCommandsFlushOutputBeforeExit() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        FlushTrackingPrintWriter stdout = new FlushTrackingPrintWriter();
        CommandLine commandLine = ZoltCli.newCommandLine();
        commandLine.setOut(stdout);
        commandLine.setErr(new PrintWriter(new StringWriter()));

        int exitCode = commandLine.execute("conflicts", "--cwd", projectDir.toString());

        assertEquals(0, exitCode);
        assertEquals("No dependency conflicts found.\n", stdout.content());
        assertTrue(stdout.flushed());
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
    void classpathRejectsUnknownKindWithSupportedKinds() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute("classpath", "--cwd", projectDir.toString(), "processor-test");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Unknown classpath kind `processor-test`"));
        assertTrue(result.stderr().contains("compile, runtime, test, processor, test-processor, or quarkus-deployment"));
    }

    @Test
    void classpathReportsMissingLockfileCleanly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);

        CommandResult result = execute("classpath", "--cwd", projectDir.toString(), "runtime");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.lock"));
    }

    @Test
    void quarkusPlanPrintsAugmentationInputsFromLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        enableQuarkus(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "io.quarkus:quarkus-rest"
                version = "3.33.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar"
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
        Path root = projectDir.toAbsolutePath().normalize();
        Path runtimeJar = cacheRoot.resolve("io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar");
        Path deploymentJar = cacheRoot.resolve(
                "io/quarkus/quarkus-rest-deployment/3.33.0/quarkus-rest-deployment-3.33.0.jar");
        createJarWithTextEntry(
                runtimeJar,
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0\n");

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        String inputFingerprint = quarkusInputFingerprint(result.stdout());
        assertTrue(inputFingerprint.matches("sha256:[0-9a-f]{64}"));
        assertEquals("""
                Quarkus augmentation plan
                Status: inputs resolved; augmentation runner not implemented yet
                Application classes: %s
                Package target: fast-jar
                Augmentation output: %s
                Package output: %s
                Input fingerprint: %s
                Augmentation metadata: missing (%s)
                Runtime classpath entries: 1
                  %s
                Deployment classpath entries: 1
                  %s
                Quarkus extensions: 1
                  io.quarkus:quarkus-rest -> io.quarkus:quarkus-rest-deployment:3.33.0
                    runtime jar: %s
                    deployment jar: %s
                Next: implement the Zolt-owned Quarkus augmentation runner with these inputs.
                """.formatted(
                root.resolve("target/classes"),
                root.resolve("target/quarkus"),
                root.resolve("target/quarkus-app"),
                inputFingerprint,
                root.resolve("target/quarkus/zolt-augmentation.properties"),
                runtimeJar,
                deploymentJar,
                runtimeJar,
                deploymentJar), result.stdout());
    }

    @Test
    void quarkusPlanFailsWhenFrameworkIsNotEnabled() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(1, result.exitCode());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().contains("Quarkus is not enabled for this project"));
        assertTrue(result.stderr().contains("[framework.quarkus] enabled = true"));
    }

    @Test
    void quarkusPlanReportsCurrentAugmentationMetadata() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        enableQuarkus(projectDir);
        writeQuarkusPlanLockfile(projectDir);
        Path runtimeJar = cacheRoot.resolve("io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar");
        createJarWithTextEntry(
                runtimeJar,
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0\n");
        String fingerprint = quarkusInputFingerprint(execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString()).stdout());
        writeQuarkusAugmentationMetadata(projectDir, fingerprint);

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Augmentation metadata: current"));
        assertTrue(result.stdout().contains("recorded " + fingerprint));
    }

    @Test
    void quarkusPlanReportsStaleAugmentationMetadata() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        enableQuarkus(projectDir);
        writeQuarkusPlanLockfile(projectDir);
        Path runtimeJar = cacheRoot.resolve("io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar");
        createJarWithTextEntry(
                runtimeJar,
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0\n");
        writeQuarkusAugmentationMetadata(projectDir, "sha256:" + "0".repeat(64));

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Augmentation metadata: stale"));
        assertTrue(result.stdout().contains("recorded sha256:" + "0".repeat(64)));
    }

    @Test
    void quarkusPlanFailsWhenNoDeploymentInputsAreResolved() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        enableQuarkus(projectDir);
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
                """);

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("Status: not ready"));
        assertTrue(result.stdout().contains("Deployment classpath entries: 0"));
        assertTrue(result.stderr().contains("No Quarkus deployment artifacts were found in zolt.lock"));
        assertTrue(result.stderr().contains("run `zolt resolve`"));
    }

    @Test
    void quarkusPlanFailsWhenRuntimeExtensionDeploymentIsMissingFromLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        enableQuarkus(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "io.quarkus:quarkus-rest"
                version = "3.33.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar"
                dependencies = []

                [[package]]
                id = "io.quarkus:quarkus-arc-deployment"
                version = "3.33.0"
                source = "maven-central"
                scope = "quarkus-deployment"
                direct = false
                jar = "io/quarkus/quarkus-arc-deployment/3.33.0/quarkus-arc-deployment-3.33.0.jar"
                dependencies = []
                """);
        Path runtimeJar = cacheRoot.resolve("io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar");
        createJarWithTextEntry(
                runtimeJar,
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0\n");

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("io.quarkus:quarkus-rest -> io.quarkus:quarkus-rest-deployment:3.33.0"));
        assertTrue(result.stdout().contains("deployment jar: missing from zolt.lock"));
        assertTrue(result.stderr().contains("matching deployment artifacts"));
        assertTrue(result.stderr().contains("Run `zolt resolve`"));
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
                    ]
                  },
                  "diagnostics": []
                }
                """.formatted(
                currentJavaMajorVersion(),
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
                jsonPath(testJar)), result.stdout());
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
    void ideModelCheckLockReportsFreshLockfileWithoutDiagnostics() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
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
    void ideModelCheckLockReportsStaleLockfileWithoutRewritingIt() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
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
                    "--check-lock",
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
        assertTrue(result.stdout().contains("retry zolt ide model --check-lock --offline"));
        assertEquals("version = 1\n", Files.readString(projectDir.resolve("zolt.lock")));
    }

    @Test
    void ideModelWorkspacePrintsWorkspaceJson() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                defaultMembers = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));
        Files.writeString(apiDir.resolve("zolt.lock"), "version = 1\n");
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Files.writeString(coreDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "ide",
                "model",
                "--workspace",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\"workspace\": {"));
        assertTrue(result.stdout().contains("\"name\": \"workspace\""));
        assertTrue(result.stdout().contains("\"members\": ["));
        assertTrue(result.stdout().contains("\"apps/api\""));
        assertTrue(result.stdout().contains("\"modules/core\""));
        assertTrue(result.stdout().contains("\"projects\": ["));
        assertTrue(result.stdout().contains("\"member\": \"apps/api\""));
        assertTrue(result.stdout().contains("\"member\": \"modules/core\""));
        assertTrue(result.stdout().contains("\"edges\": []"));
        assertTrue(result.stdout().contains("\"diagnostics\": []"));
    }

    @Test
    void resolveWorkspaceWritesRootLockfile() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                defaultMembers = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));

        CommandResult result = execute(
                "resolve",
                "--workspace",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Resolved 0 packages"));
        assertTrue(result.stdout().contains("Wrote " + workspaceDir.resolve("zolt.lock")));
        assertEquals("version = 1\n\n", Files.readString(workspaceDir.resolve("zolt.lock")));
        assertFalse(Files.exists(apiDir.resolve("zolt.lock")));
        assertFalse(Files.exists(coreDir.resolve("zolt.lock")));
    }

    @Test
    void buildResolvesMissingLockfileAndCompilesMainSources() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """);

        CommandResult result = execute(
                "build",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Resolved dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("Compiled 1 main source files"));
        assertTrue(result.stdout().contains("Wrote classes to " + projectDir.resolve("target/classes")));
        assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
        assertTrue(Files.exists(projectDir.resolve("target/classes/com/example/Main.class")));
    }

    @Test
    void buildRunsQuarkusAugmentationWhenFrameworkIsEnabled() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        enableQuarkus(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "build",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("Compiled 0 main source files"));
        assertTrue(result.stdout().contains("Wrote classes to " + projectDir.resolve("target/classes")));
        assertTrue(result.stderr().contains("No Quarkus deployment artifacts were found in zolt.lock"));
        assertTrue(result.stderr().contains("run `zolt resolve`"));
    }

    @Test
    void buildWorkspaceCompilesMembersInDependencyOrder() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);

        CommandResult result = execute(
                "build",
                "--workspace",
                "--all",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Resolved workspace dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in modules/core"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in apps/api"));
        assertTrue(result.stdout().contains("Compiled 2 workspace main source files"));
        assertTrue(Files.exists(coreDir.resolve("target/classes/com/example/core/Core.class")));
        assertTrue(Files.exists(apiDir.resolve("target/classes/com/example/api/Api.class")));
    }

    @Test
    void buildWorkspaceMemberSelectionCompilesDependenciesOnly() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Path workerDir = workspaceDir.resolve("apps/worker");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.createDirectories(workerDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core", "apps/worker"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);
        Files.writeString(workerDir.resolve("zolt.toml"), memberConfig("worker"));
        Path workerSource = workerDir.resolve("src/main/java/com/example/worker/Worker.java");
        Files.createDirectories(workerSource.getParent());
        Files.writeString(workerSource, """
                package com.example.worker;

                public final class Worker {
                }
                """);

        CommandResult result = execute(
                "build",
                "--workspace",
                "--member", "apps/api",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Compiled 1 main source files in modules/core"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in apps/api"));
        assertFalse(result.stdout().contains("apps/worker"));
        assertTrue(Files.exists(coreDir.resolve("target/classes/com/example/core/Core.class")));
        assertTrue(Files.exists(apiDir.resolve("target/classes/com/example/api/Api.class")));
        assertFalse(Files.exists(workerDir.resolve("target/classes/com/example/worker/Worker.class")));
    }

    @Test
    void buildWorkspaceMembersOptionSelectsCommaSeparatedMembers() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Path workerDir = workspaceDir.resolve("apps/worker");
        Path adminDir = workspaceDir.resolve("apps/admin");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.createDirectories(workerDir);
        Files.createDirectories(adminDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core", "apps/worker", "apps/admin"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);
        Files.writeString(workerDir.resolve("zolt.toml"), memberConfig("worker"));
        Path workerSource = workerDir.resolve("src/main/java/com/example/worker/Worker.java");
        Files.createDirectories(workerSource.getParent());
        Files.writeString(workerSource, """
                package com.example.worker;

                public final class Worker {
                }
                """);
        Files.writeString(adminDir.resolve("zolt.toml"), memberConfig("admin"));
        Path adminSource = adminDir.resolve("src/main/java/com/example/admin/Admin.java");
        Files.createDirectories(adminSource.getParent());
        Files.writeString(adminSource, """
                package com.example.admin;

                public final class Admin {
                }
                """);

        CommandResult result = execute(
                "build",
                "--workspace",
                "--members", "apps/api,apps/worker",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Compiled 1 main source files in modules/core"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in apps/api"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in apps/worker"));
        assertFalse(result.stdout().contains("apps/admin"));
        assertTrue(Files.exists(coreDir.resolve("target/classes/com/example/core/Core.class")));
        assertTrue(Files.exists(apiDir.resolve("target/classes/com/example/api/Api.class")));
        assertTrue(Files.exists(workerDir.resolve("target/classes/com/example/worker/Worker.class")));
        assertFalse(Files.exists(adminDir.resolve("target/classes/com/example/admin/Admin.class")));
    }

    @Test
    void workspaceMembersOptionConflictsWithAll() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Files.createDirectories(apiDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));

        CommandResult result = execute(
                "build",
                "--workspace",
                "--all",
                "--members", "apps/api",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Use either --all or member selection for workspace selection, not both."));
    }

    @Test
    void testWorkspaceRunsMembersInDependencyOrder() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Path cacheRoot = tempDir.resolve("cache");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        writeFakeConsoleJar(cacheRoot.resolve(
                "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"));
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);
        Path apiTest = apiDir.resolve("src/test/java/com/example/api/ApiTest.java");
        Files.createDirectories(apiTest.getParent());
        Files.writeString(apiTest, """
                package com.example.api;

                import com.example.core.Core;

                public final class ApiTest {
                    public String message() {
                        return Api.message() + Core.message();
                    }
                }
                """);
        Files.writeString(workspaceDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:core"
                version = "0.1.0"
                source = "workspace"
                scope = "compile"
                direct = true
                workspace = "modules/core"
                workspaceOutput = "target/classes"
                dependencies = []

                [[package]]
                id = "org.junit.platform:junit-platform-console-standalone"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
                dependencies = []
                """);

        CommandResult result = execute(
                "test",
                "--workspace",
                "--all",
                "--cwd", apiDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("fake console"));
        assertTrue(result.stdout().contains("Tests passed in modules/core"));
        assertTrue(result.stdout().contains("Tests passed in apps/api"));
        assertTrue(result.stdout().contains("Tests passed for 2 workspace members"));
        assertTrue(Files.exists(coreDir.resolve("target/classes/com/example/core/Core.class")));
        assertTrue(Files.exists(apiDir.resolve("target/classes/com/example/api/Api.class")));
        assertTrue(Files.exists(apiDir.resolve("target/test-classes/com/example/api/ApiTest.class")));
    }

    @Test
    void packageWorkspaceMemberPackagesSelectedMemberOnly() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """
                main = "com.example.api.Api"

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static void main(String[] args) {
                        System.out.println(Core.message());
                    }
                }
                """);

        CommandResult result = execute(
                "package",
                "--workspace",
                "--member", "apps/api",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Resolved workspace dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("Packaged 1 compiled files as thin jar in apps/api"));
        assertTrue(result.stdout().contains("Included Main-Class manifest entry in apps/api"));
        assertTrue(result.stdout().contains("Packaged 1 workspace members"));
        assertTrue(Files.exists(apiDir.resolve("target/api-0.1.0.jar")));
        assertFalse(Files.exists(coreDir.resolve("target/core-0.1.0.jar")));
    }

    @Test
    void runPackageWorkspaceMemberRunsSelectedPackagedApplication() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """
                main = "com.example.api.Api"

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static void main(String[] args) {
                        System.out.println(Core.message() + ":" + args[0]);
                    }
                }
                """);

        CommandResult result = execute(
                "run-package",
                "--workspace",
                "--member", "apps/api",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "hello");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Resolved workspace dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("core:hello"));
        assertTrue(result.stdout().contains("Ran packaged com.example.api.Api in apps/api from "));
        assertTrue(Files.exists(apiDir.resolve("target/api-0.1.0.jar")));
        assertFalse(Files.exists(coreDir.resolve("target/core-0.1.0.jar")));
    }

    @Test
    void runWorkspaceMemberRunsSelectedApplicationFromClasses() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """
                main = "com.example.api.Api"

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static void main(String[] args) {
                        System.out.println(Core.message() + ":" + args[0]);
                    }
                }
                """);

        CommandResult result = execute(
                "run",
                "--workspace",
                "--member", "apps/api",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "hello");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Resolved workspace dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("core:hello"));
        assertTrue(result.stdout().contains("Ran com.example.api.Api in apps/api"));
        assertTrue(Files.exists(apiDir.resolve("target/classes/com/example/api/Api.class")));
        assertTrue(Files.exists(coreDir.resolve("target/classes/com/example/core/Core.class")));
        assertFalse(Files.exists(apiDir.resolve("target/api-0.1.0.jar")));
    }

    @Test
    void buildOfflineUsesExistingLockfileAndCache() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """);

        CommandResult result = execute(
                "build",
                "--offline",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Compiled 1 main source files"));
        assertTrue(Files.exists(projectDir.resolve("target/classes/com/example/Main.class")));
    }

    @Test
    void buildReturnsNonZeroOnCompilationFailure() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    missing
                }
                """);

        CommandResult result = execute(
                "build",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: javac failed with exit code"));
        assertTrue(result.stderr().contains("Main.java"));
    }

    @Test
    void runBuildsAndRunsConfiguredMainClass() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("hello " + args[0] + " " + args[1]);
                    }
                }
                """);

        CommandResult result = execute(
                "run",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--",
                "one",
                "two");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("hello one two"));
        assertTrue(result.stdout().contains("Ran com.example.Main"));
        assertTrue(Files.exists(projectDir.resolve("target/classes/com/example/Main.class")));
    }

    @Test
    void runReportsMissingMainClassClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfigWithoutMain(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "run",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("No main class is configured"));
    }

    @Test
    void packageBuildsAndWritesJarWithManifest() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """);

        CommandResult result = execute(
                "package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Resolved dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("Packaged 1 compiled files as thin jar"));
        assertTrue(result.stdout().contains("Included Main-Class manifest entry"));
        assertTrue(result.stdout().contains("Run with: java -jar " + jarPath));
        assertTrue(result.stdout().contains("Run with dependencies: zolt run-package -- [args]"));
        assertTrue(result.stdout().contains("Thin jar: dependencies are not bundled."));
        assertTrue(result.stdout().contains(
                "Wrote runtime classpath to " + projectDir.resolve("target/demo-0.1.0.runtime-classpath")));
        assertTrue(result.stdout().contains("Wrote jar to " + jarPath));
        assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
        assertTrue(Files.exists(projectDir.resolve("target/demo-0.1.0.runtime-classpath")));
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            assertNotNull(jar.getEntry("com/example/Main.class"));
            assertEquals(
                    "com.example.Main",
                    jar.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
        }
    }

    @Test
    void packageModeOverrideUsesThinForCurrentCommandOnly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.toml"), Files.readString(projectDir.resolve("zolt.toml")) + """

                [package]
                mode = "spring-boot"
                """);
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """);

        CommandResult result = execute(
                "package",
                "--mode", "thin",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Packaged 1 compiled files as thin jar"));
        assertTrue(Files.readString(projectDir.resolve("zolt.toml")).contains("mode = \"spring-boot\""));
        assertTrue(Files.exists(projectDir.resolve("target/demo-0.1.0.jar")));
    }

    @Test
    void packageBuildsSpringBootJarWhenLoaderIsResolved() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.toml"), Files.readString(projectDir.resolve("zolt.toml")) + """

                [package]
                mode = "spring-boot"
                """);
        writeSpringBootLockfile(projectDir, cacheRoot);
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """);

        CommandResult result = execute(
                "package",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Packaged 1 compiled files as spring-boot jar"));
        assertTrue(result.stdout().contains("Run with: java -jar " + jarPath));
        assertTrue(result.stdout().contains("Run with Zolt: zolt run-package --mode spring-boot -- [args]"));
        assertTrue(result.stdout().contains("Spring Boot jar: dependencies are nested under BOOT-INF/lib."));
        assertFalse(result.stdout().contains("Thin jar: dependencies are not bundled."));
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            assertNotNull(jar.getEntry("BOOT-INF/classes/com/example/Main.class"));
            assertNotNull(jar.getEntry("BOOT-INF/lib/runtime-lib-1.0.0.jar"));
            assertNotNull(jar.getEntry("org/springframework/boot/loader/launch/JarLauncher.class"));
            assertEquals(
                    "com.example.Main",
                    jar.getManifest().getMainAttributes().getValue("Start-Class"));
        }
    }

    @Test
    void packageReportsMissingSpringBootLoaderClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.toml"), Files.readString(projectDir.resolve("zolt.toml")) + """

                [package]
                mode = "spring-boot"
                """);
        writeMainSource(projectDir, "package com.example; public final class Main {}\n");
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("requires `org.springframework.boot:spring-boot-loader`"));
        assertTrue(result.stderr().contains("run `zolt resolve`"));
        assertFalse(Files.exists(projectDir.resolve("target/demo-0.1.0.jar")));
    }

    @Test
    void packageRejectsUnknownModeOverride() {
        CommandResult result = execute(
                "package",
                "--mode", "war",
                "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Unsupported package mode `war`"));
        assertTrue(result.stderr().contains("thin, spring-boot, quarkus, uber"));
    }

    @Test
    void packageReturnsNonZeroOnPackagingFailure() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                main = "com.example.Main"

                [repositories]
                central = "https://repo.maven.apache.org/maven2"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "classes"
                testOutput = "test-classes"
                """.formatted(currentJavaMajorVersion()));
        writeMainSource(projectDir, "package com.example; public final class Main {}\n");
        Files.writeString(projectDir.resolve("target"), "not a directory");

        CommandResult result = execute(
                "package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Could not package jar"));
        assertTrue(result.stderr().contains("Check that target/ is writable"));
    }

    @Test
    void runPackageBuildsThinJarAndRunsConfiguredMainClass() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("packaged " + args[0] + " " + args[1]);
                    }
                }
                """);

        CommandResult result = execute(
                "run-package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--",
                "one",
                "two");

        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("packaged one two"));
        assertTrue(result.stdout().contains("Ran packaged com.example.Main from " + jarPath));
        assertTrue(Files.exists(jarPath));
    }

    @Test
    void runPackageReportsMissingMainClassClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfigWithoutMain(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "run-package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("No main class is configured"));
        assertTrue(result.stderr().contains("[project].main"));
    }

    @Test
    void nativeReportsMissingMainClassClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfigWithoutMain(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "native",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Native Image main class is missing"));
        assertTrue(result.stderr().contains("[project].main"));
    }

    @Test
    void releaseArchiveAssemblesArchiveFromNativeBinary() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("README.md"), "# Demo\n");
        Path binary = projectDir.resolve("target/native/demo");
        Files.createDirectories(binary.getParent());
        Files.writeString(binary, "native");

        CommandResult result = execute(
                "release-archive",
                "--cwd", projectDir.toString(),
                "--target", "linux-x64");

        Path archive = projectDir.resolve("dist/demo-0.1.0-linux-x64.tar.gz");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Assembled linux-x64 release archive"));
        assertTrue(result.stdout().contains("Included 2 files under demo-0.1.0-linux-x64"));
        assertTrue(result.stdout().contains("Wrote archive to " + archive));
        assertTrue(result.stdout().contains("Wrote checksum to " + archive + ".sha256"));
        assertTrue(result.stdout().contains("Wrote manifest to " + projectDir.resolve("dist/release-manifest.json")));
        assertTrue(Files.exists(archive));
        assertTrue(Files.exists(projectDir.resolve("dist/demo-0.1.0-linux-x64.tar.gz.sha256")));
        assertTrue(Files.exists(projectDir.resolve("dist/release-manifest.json")));
    }

    @Test
    void releaseArchiveReportsMissingBinaryClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "release-archive",
                "--cwd", projectDir.toString(),
                "--target", "linux-x64");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Release archive requires native binary"));
        assertTrue(result.stderr().contains("Run `zolt native` or pass --binary <path>"));
    }

    @Test
    void releaseVerifyReportsMissingArchiveClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "release-verify",
                "--cwd", projectDir.toString(),
                "dist/missing.tar.gz");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Release archive verification failed for"));
        assertTrue(result.stderr().contains("archive does not exist"));
        assertTrue(result.stderr().contains("Pass a valid release archive path"));
    }

    @Test
    void cleanDeletesBuildOutputWithoutDeletingCache() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.createDirectories(projectDir.resolve("target/classes"));
        Files.writeString(projectDir.resolve("target/classes/Main.class"), "compiled");
        Files.createDirectories(projectDir.resolve(".zolt/cache"));
        Files.writeString(projectDir.resolve(".zolt/cache/artifact.jar"), "cached");

        CommandResult result = execute("clean", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Deleted 1 build output paths"));
        assertFalse(Files.exists(projectDir.resolve("target")));
        assertTrue(Files.exists(projectDir.resolve(".zolt/cache/artifact.jar")));
    }

    @Test
    void cleanDeletesQuarkusOutputLayoutWhenEnabled() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(
                projectDir.resolve("zolt.toml"),
                Files.readString(projectDir.resolve("zolt.toml"))
                        .replace("output = \"target/classes\"", "output = \"out/main\"")
                        .replace("testOutput = \"target/test-classes\"", "testOutput = \"out/test\""));
        enableQuarkus(projectDir);
        Files.createDirectories(projectDir.resolve("out/main"));
        Files.writeString(projectDir.resolve("out/main/Main.class"), "compiled");
        Files.createDirectories(projectDir.resolve("target/quarkus"));
        Files.writeString(projectDir.resolve("target/quarkus/zolt-augmentation.properties"), "metadata");
        Files.createDirectories(projectDir.resolve("target/quarkus-app"));
        Files.writeString(projectDir.resolve("target/quarkus-app/quarkus-run.jar"), "jar");

        CommandResult result = execute("clean", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Deleted 3 build output paths"));
        assertFalse(Files.exists(projectDir.resolve("out/main")));
        assertFalse(Files.exists(projectDir.resolve("target/quarkus")));
        assertFalse(Files.exists(projectDir.resolve("target/quarkus-app")));
    }

    @Test
    void cleanHandlesMissingTargetCleanly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute("clean", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals("Nothing to clean\n", result.stdout());
    }

    @Test
    void testReportsMissingJUnitConsoleClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, "package com.example; public final class Main {}\n");
        Path testSource = projectDir.resolve("src/test/java/com/example/MainTest.java");
        Files.createDirectories(testSource.getParent());
        Files.writeString(testSource, "package com.example; public final class MainTest {}\n");

        CommandResult result = execute(
                "test",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("JUnit Platform Console is not present"));
    }

    @Test
    void doctorReportsJdkStatus() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2", currentJavaMajorVersion());

        CommandResult result = execute("doctor", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("JDK status: ok"));
        assertTrue(result.stdout().contains("java: "));
        assertTrue(result.stdout().contains("javac: "));
        assertTrue(result.stdout().contains("jar: "));
        assertTrue(result.stdout().contains("version: " + currentJavaMajorVersion()));
    }

    @Test
    void doctorReportsSelfHostingReadiness() throws IOException {
        Path projectDir = tempDir.resolve("self-hosting-ready");
        writeSelfHostingProjectConfig(projectDir, true);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        Files.createDirectories(projectDir.resolve("src/main/java"));
        Files.createDirectories(projectDir.resolve("src/test/java"));

        CommandResult result = execute("doctor", "--self-hosting", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Self-hosting status: ok"));
        assertTrue(result.stdout().contains("ok: main class - project main is com.example.Main"));
        assertTrue(result.stdout().contains("ok: JUnit Platform Console"));
        assertTrue(result.stdout().contains("ok: native no-fallback"));
    }

    @Test
    void doctorReportsSelfHostingGapsWithNextSteps() throws IOException {
        Path projectDir = tempDir.resolve("self-hosting-gaps");
        writeSelfHostingProjectConfig(projectDir, false);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        Files.createDirectories(projectDir.resolve("src/main/java"));
        Files.createDirectories(projectDir.resolve("src/test/java"));

        CommandResult result = execute("doctor", "--self-hosting", "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("Self-hosting status: error"));
        assertTrue(result.stdout().contains("error: JUnit Platform Console - add org.junit.platform:junit-platform-console-standalone to [test.dependencies]"));
    }

    private static CommandResult execute(String... args) {
        CommandLine commandLine = ZoltCli.newCommandLine();
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        commandLine.setOut(new PrintWriter(stdout));
        commandLine.setErr(new PrintWriter(stderr));

        int exitCode = commandLine.execute(args);

        return new CommandResult(exitCode, stdout.toString(), stderr.toString());
    }

    private static void writeSelfHostingProjectConfig(Path projectDir, boolean includeTestRunner) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                main = "com.example.Main"

                [repositories]
                central = "https://repo.maven.apache.org/maven2"

                %s
                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"

                [native]
                imageName = "demo"
                output = "target/native"
                args = ["--no-fallback"]
                """.formatted(
                currentJavaMajorVersion(),
                includeTestRunner
                        ? """
                        [test.dependencies]
                        "org.junit.platform:junit-platform-console-standalone" = "1.11.4"

                        """
                        : ""));
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }

    private static final class FlushTrackingPrintWriter extends PrintWriter {
        private final StringWriter writer;
        private boolean flushed;

        private FlushTrackingPrintWriter() {
            this(new StringWriter());
        }

        private FlushTrackingPrintWriter(StringWriter writer) {
            super(writer);
            this.writer = writer;
        }

        @Override
        public void flush() {
            flushed = true;
            super.flush();
        }

        private boolean flushed() {
            return flushed;
        }

        private String content() {
            return writer.toString();
        }
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
            String javaVersion) throws IOException {
        writeProjectConfig(projectDir, repositoryUrl, javaVersion, Map.of(), Map.of());
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

    private static void enableQuarkus(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("zolt.toml"), Files.readString(projectDir.resolve("zolt.toml")) + """

                [framework.quarkus]
                enabled = true
                package = "fast-jar"
                """);
    }

    private static void writeProjectConfigWithoutMain(Path projectDir, String repositoryUrl) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"

                [repositories]
                test = "%s"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """.formatted(currentJavaMajorVersion(), repositoryUrl));
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

    private static String memberConfig(String name) {
        return """
                [project]
                name = "%s"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                """.formatted(name, currentJavaMajorVersion());
    }

    private static void writeMainSource(Path projectDir, String content) throws IOException {
        Path source = projectDir.resolve("src/main/java/com/example/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }

    private static void writeFakeConsoleJar(Path jar) throws IOException {
        Path workDir = jar.getParent().resolve("fake-console-work");
        Path source = workDir.resolve("src/org/junit/platform/console/ConsoleLauncher.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package org.junit.platform.console;

                public final class ConsoleLauncher {
                    private ConsoleLauncher() {
                    }

                    public static void main(String[] args) {
                        System.out.println("fake console");
                    }
                }
                """);
        Path classes = workDir.resolve("classes");
        new com.zolt.build.JavacRunner().compile(
                currentJavac(),
                java.util.List.of(source),
                new com.zolt.resolve.Classpath(java.util.List.of()),
                classes);

        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            JarEntry entry = new JarEntry("org/junit/platform/console/ConsoleLauncher.class");
            output.putNextEntry(entry);
            output.write(Files.readAllBytes(classes.resolve("org/junit/platform/console/ConsoleLauncher.class")));
            output.closeEntry();
        }
    }

    private static void writeSpringBootLockfile(Path projectDir, Path cacheRoot) throws IOException {
        createJarWithEntry(
                cacheRoot.resolve("org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar"),
                "org/springframework/boot/SpringApplication.class");
        createJarWithEntry(
                cacheRoot.resolve("org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar"),
                "org/springframework/boot/loader/launch/JarLauncher.class");
        createJarWithEntry(
                cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"),
                "com/example/runtime/RuntimeLib.class");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.springframework.boot:spring-boot"
                version = "4.0.6"
                source = "maven-central"
                scope = "compile"
                direct = false
                jar = "org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar"
                dependencies = []

                [[package]]
                id = "org.springframework.boot:spring-boot-loader"
                version = "4.0.6"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar"
                dependencies = []

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                dependencies = []
                """);
    }

    private static void createJarWithEntry(Path jar, String entryName) throws IOException {
        createJarWithTextEntry(jar, entryName, "\0");
    }

    private static void createJarWithTextEntry(Path jar, String entryName, String text) throws IOException {
        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(entryName));
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    private static Path currentJavac() {
        return Path.of(System.getProperty("java.home")).resolve("bin").resolve(executable("javac"));
    }

    private static String executable(String name) {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? name + ".exe"
                : name;
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int index = value.indexOf(needle);
        while (index >= 0) {
            count++;
            index = value.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static String jsonPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String quarkusInputFingerprint(String output) {
        return output.lines()
                .filter(line -> line.startsWith("Input fingerprint: "))
                .findFirst()
                .orElseThrow()
                .substring("Input fingerprint: ".length());
    }

    private static void writeQuarkusPlanLockfile(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "io.quarkus:quarkus-rest"
                version = "3.33.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar"
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
    }

    private static void writeQuarkusAugmentationMetadata(Path projectDir, String inputFingerprint) throws IOException {
        Path metadata = projectDir.resolve("target/quarkus/zolt-augmentation.properties");
        Files.createDirectories(metadata.getParent());
        Files.writeString(metadata, """
                version=1
                inputFingerprint=%s
                """.formatted(inputFingerprint));
    }

    private static final class TestRepository implements AutoCloseable {
        private final HttpServer server;
        private final Map<String, byte[]> responses = new HashMap<>();
        private final URI baseUri;

        private TestRepository(HttpServer server) {
            this.server = server;
            this.baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/maven2/");
        }

        static TestRepository start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            TestRepository repository = new TestRepository(server);
            server.createContext("/", repository::handle);
            server.start();
            return repository;
        }

        URI baseUri() {
            return baseUri;
        }

        void addArtifact(String groupId, String artifactId, String version, String pom) {
            String base = "/maven2/"
                    + groupId.replace('.', '/')
                    + "/"
                    + artifactId
                    + "/"
                    + version
                    + "/"
                    + artifactId
                    + "-"
                    + version;
            responses.put(base + ".pom", pom.getBytes(StandardCharsets.UTF_8));
            responses.put(base + ".jar", new byte[] {0x50, 0x4b, 0x03, 0x04});
        }

        private void handle(HttpExchange exchange) throws IOException {
            byte[] body = responses.get(exchange.getRequestURI().getPath());
            if (body == null) {
                respond(exchange, 404, "missing".getBytes(StandardCharsets.UTF_8));
                return;
            }
            respond(exchange, 200, body);
        }

        private static void respond(HttpExchange exchange, int statusCode, byte[] body) throws IOException {
            try (exchange) {
                exchange.sendResponseHeaders(statusCode, body.length);
                exchange.getResponseBody().write(body);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
