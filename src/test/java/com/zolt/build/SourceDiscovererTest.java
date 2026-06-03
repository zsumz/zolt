package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SourceDiscovererTest {
    private final SourceDiscoverer discoverer = new SourceDiscoverer();

    @TempDir
    private Path projectDir;

    @Test
    void findsMainAndTestJavaSources() throws IOException {
        Path main = source("src/main/java/com/example/Main.java");
        Path nested = source("src/main/java/com/example/internal/Helper.java");
        Path test = source("src/test/java/com/example/MainTest.java");
        source("src/main/java/com/example/readme.txt");

        SourceDiscoveryResult result = discoverer.discover(projectDir, BuildSettings.defaults());

        assertEquals(List.of(main, nested), result.mainSources());
        assertEquals(List.of(test), result.testSources());
    }

    @Test
    void findsJavaTestsFromMultipleRootsDeterministically() throws IOException {
        Path unit = source("src/test/java/com/example/MainTest.java");
        Path integration = source("src/integration-test/java/com/example/MainIT.java");
        Path fixture = source("src/fixtures/java/com/example/FixtureTest.java");

        SourceDiscoveryResult result = discoverer.discover(
                projectDir,
                new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of(
                                "src/integration-test/java",
                                "src/test/java",
                                "src/fixtures/java")));

        assertEquals(List.of(fixture, integration, unit), result.testSources());
    }

    @Test
    void deDuplicatesOverlappingTestRoots() throws IOException {
        Path test = source("src/test/java/com/example/MainTest.java");

        SourceDiscoveryResult result = discoverer.discover(
                projectDir,
                new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test", "src/test/java")));

        assertEquals(List.of(test), result.testSources());
    }

    @Test
    void ignoresTargetAndBuildOutputDirectories() throws IOException {
        Path app = source("src/main/java/com/example/App.java");
        source("target/classes/com/example/Generated.java");
        source("build/generated/com/example/Generated.java");
        source("target/test-classes/com/example/GeneratedTest.java");

        SourceDiscoveryResult result = discoverer.discover(
                projectDir,
                new BuildSettings(".", ".", "target/classes", "target/test-classes"));

        assertEquals(List.of(app), result.mainSources());
        assertEquals(List.of(app), result.testSources());
    }

    @Test
    void doesNotIgnoreJavaPackageNamedBuild() throws IOException {
        Path buildPackage = source("src/main/java/com/example/build/Tool.java");

        SourceDiscoveryResult result = discoverer.discover(projectDir, BuildSettings.defaults());

        assertEquals(List.of(buildPackage), result.mainSources());
    }

    @Test
    void missingSourceDirectoriesReturnEmptyLists() {
        SourceDiscoveryResult result = discoverer.discover(projectDir, BuildSettings.defaults());

        assertTrue(result.empty());
        assertTrue(result.mainSources().isEmpty());
        assertTrue(result.testSources().isEmpty());
    }

    private Path source(String path) throws IOException {
        Path source = projectDir.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "final class Example {}\n");
        return source.normalize();
    }
}
