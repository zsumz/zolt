package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static com.zolt.cli.CliTestSupport.writeFakeConsoleJar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void testCommandWritesJUnitReportsWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("reports-demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeFakeConsoleJar(cacheRoot.resolve(
                "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"));
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("reports-demo"));
        writeJUnitConsoleLockfile(projectDir);
        writeDemoTestSource(projectDir);

        CommandResult result = execute(
                "test",
                "--reports-dir", "target/test-reports",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        Path report = projectDir.resolve("target/test-reports/TEST-fake-console.xml");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("fake console"));
        assertTrue(result.stdout().contains("Tests passed"));
        assertTrue(result.stdout().contains("Wrote test reports to "
                + projectDir.resolve("target/test-reports").toAbsolutePath().normalize()));
        assertTrue(Files.exists(report));
        assertTrue(Files.readString(report).contains("testsuite"));
    }

    @Test
    void testCommandPrintsRequestedEventOutput() throws IOException {
        Path projectDir = tempDir.resolve("events-demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeFakeConsoleJar(cacheRoot.resolve(
                "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"));
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("events-demo"));
        writeJUnitConsoleLockfile(projectDir);
        writeDemoTestSource(projectDir);

        CommandResult result = execute(
                "test",
                "--test-event", "failed",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("fake console"));
        assertTrue(result.stdout().contains("fake console event output"));
        assertTrue(result.stdout().contains("Tests passed"));
    }

    @Test
    void testCommandPrintsNestedJsonTimingsWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeFakeConsoleJar(cacheRoot.resolve(
                "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"));
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo"));
        writeJUnitConsoleLockfile(projectDir);
        writeDemoTestSource(projectDir);

        CommandResult result = execute(
                "test",
                "--tests", "*DemoTest",
                "--include-tag", "fast",
                "--exclude-tag", "slow",
                "--timings",
                "--timings-format", "json",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("fake console"));
        assertTrue(result.stdout().contains("Tests passed"));
        String[] lines = result.stderr().lines().toArray(String[]::new);
        assertEquals(6, lines.length);
        assertTrue(lines[0].contains("\"phase\":\"config read\""));
        assertTrue(lines[0].contains("\"depth\":0"));
        assertTrue(lines[1].contains("\"phase\":\"build test inputs\""));
        assertTrue(lines[1].contains("\"depth\":2"));
        assertTrue(lines[1].contains("\"mainCompilationSkipped\""));
        assertTrue(lines[1].contains("\"mainCompilationMode\""));
        assertTrue(lines[1].contains("\"mainIncrementalFallbackReason\""));
        assertTrue(lines[1].contains("\"mainSourcesRecompiled\""));
        assertTrue(lines[1].contains("\"mainDependentSourcesRecompiled\""));
        assertTrue(lines[1].contains("\"mainAbiChangedClasses\""));
        assertTrue(lines[1].contains("\"mainFingerprintCheckNanos\""));
        assertTrue(lines[1].contains("\"mainFingerprintWriteNanos\""));
        assertTrue(lines[2].contains("\"phase\":\"compile test sources\""));
        assertTrue(lines[2].contains("\"depth\":2"));
        assertTrue(lines[2].contains("\"testSourceFiles\":\"1\""));
        assertTrue(lines[2].contains("\"testCompilationSkipped\":\"false\""));
        assertTrue(lines[2].contains("\"testCompilationMode\""));
        assertTrue(lines[2].contains("\"testIncrementalFallbackReason\""));
        assertTrue(lines[2].contains("\"testSourcesRecompiled\""));
        assertTrue(lines[2].contains("\"testDependentSourcesRecompiled\""));
        assertTrue(lines[2].contains("\"testAbiChangedClasses\""));
        assertTrue(lines[2].contains("\"testFingerprintCheckNanos\""));
        assertTrue(lines[2].contains("\"testFingerprintWriteNanos\""));
        assertTrue(lines[3].contains("\"phase\":\"compile tests\""));
        assertTrue(lines[3].contains("\"depth\":1"));
        assertTrue(lines[3].contains("\"testSourceFiles\":\"1\""));
        assertTrue(lines[3].contains("\"testCompilationSkipped\":\"false\""));
        assertTrue(lines[3].contains("\"testCompilationMode\""));
        assertTrue(lines[3].contains("\"testIncrementalFallbackReason\""));
        assertTrue(lines[3].contains("\"testSourcesRecompiled\""));
        assertTrue(lines[3].contains("\"testDependentSourcesRecompiled\""));
        assertTrue(lines[3].contains("\"testAbiChangedClasses\""));
        assertTrue(lines[3].contains("\"testFingerprintCheckNanos\""));
        assertTrue(lines[3].contains("\"testFingerprintWriteNanos\""));
        assertTrue(lines[4].contains("\"phase\":\"execute tests\""));
        assertTrue(lines[4].contains("\"depth\":1"));
        assertTrue(lines[4].contains("\"testRunner\":\"junit-console\""));
        assertTrue(lines[4].contains("\"testRuntimeClasspathEntries\""));
        assertTrue(lines[4].contains("\"testLauncherClasspathEntries\""));
        assertTrue(lines[4].contains("\"testDiscoveryScanRoots\""));
        assertTrue(lines[4].contains("\"testPatterns\":\"1\""));
        assertTrue(lines[4].contains("\"testIncludedTags\":\"1\""));
        assertTrue(lines[4].contains("\"testExcludedTags\":\"1\""));
        assertTrue(lines[4].contains("\"outputBytes\""));
        assertTrue(lines[5].contains("\"phase\":\"run tests\""));
        assertTrue(lines[5].contains("\"depth\":0"));
        assertTrue(lines[5].contains("\"testRunner\":\"junit-console\""));
        assertTrue(lines[5].contains("\"testSourceFiles\":\"1\""));
        assertTrue(lines[5].contains("\"testRuntimeClasspathEntries\""));
        assertTrue(lines[5].contains("\"testLauncherClasspathEntries\""));
        assertTrue(lines[5].contains("\"testDiscoveryScanRoots\""));
        assertTrue(lines[5].contains("\"testPatterns\":\"1\""));
        assertTrue(lines[5].contains("\"testIncludedTags\":\"1\""));
        assertTrue(lines[5].contains("\"testExcludedTags\":\"1\""));
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

    private static void writeJUnitConsoleLockfile(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.junit.platform:junit-platform-console-standalone"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
                dependencies = []
                """);
    }

    private static void writeDemoTestSource(Path projectDir) throws IOException {
        Path testSource = projectDir.resolve("src/test/java/com/example/DemoTest.java");
        Files.createDirectories(testSource.getParent());
        Files.writeString(testSource, "package com.example; public final class DemoTest {}\n");
    }

    private static void writeProjectConfig(Path projectDir, String repositoryUrl) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                main = "com.example.Main"

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
}
