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
import java.util.jar.JarFile;
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
                "resolve",
                "tree",
                "why",
                "conflicts",
                "classpath",
                "build",
                "run",
                "test",
                "package",
                "run-package",
                "native",
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
                """);

        Path compileJar = cacheRoot.resolve("com/example/compile-lib/1.0.0/compile-lib-1.0.0.jar");
        Path runtimeJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Path testJar = cacheRoot.resolve("com/example/test-lib/1.0.0/test-lib-1.0.0.jar");

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
        assertTrue(result.stdout().contains("Packaged 1 compiled files"));
        assertTrue(result.stdout().contains("Included Main-Class manifest entry"));
        assertTrue(result.stdout().contains("Run with: java -jar " + jarPath));
        assertTrue(result.stdout().contains("Run with dependencies: zolt run-package -- [args]"));
        assertTrue(result.stdout().contains("Thin jar: dependencies are not bundled."));
        assertTrue(result.stdout().contains("Wrote jar to " + jarPath));
        assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            assertNotNull(jar.getEntry("com/example/Main.class"));
            assertEquals(
                    "com.example.Main",
                    jar.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
        }
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

    private static CommandResult execute(String... args) {
        CommandLine commandLine = ZoltCli.newCommandLine();
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        commandLine.setOut(new PrintWriter(stdout));
        commandLine.setErr(new PrintWriter(stderr));

        int exitCode = commandLine.execute(args);

        return new CommandResult(exitCode, stdout.toString(), stderr.toString());
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

    private static void writeMainSource(Path projectDir, String content) throws IOException {
        Path source = projectDir.resolve("src/main/java/com/example/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
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
