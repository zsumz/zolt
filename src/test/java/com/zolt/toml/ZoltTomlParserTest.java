package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ZoltTomlParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesValidExampleConfig() {
        ProjectConfig config = parser.parse(Path.of("examples/hello-zolt/zolt.toml"));

        assertEquals("hello-zolt", config.project().name());
        assertEquals("0.1.0", config.project().version());
        assertEquals("com.example", config.project().group());
        assertEquals("21", config.project().java());
        assertEquals("com.example.Main", config.project().main().orElseThrow());
        assertEquals("https://repo.maven.apache.org/maven2", config.repositories().get("central"));
        assertTrue(config.platforms().isEmpty());
        assertTrue(config.apiDependencies().isEmpty());
        assertTrue(config.managedApiDependencies().isEmpty());
        assertTrue(config.workspaceApiDependencies().isEmpty());
        assertEquals("33.4.0-jre", config.dependencies().get("com.google.guava:guava"));
        assertTrue(config.testDependencies().isEmpty());
        assertTrue(config.managedDependencies().isEmpty());
        assertTrue(config.annotationProcessors().isEmpty());
        assertTrue(config.managedAnnotationProcessors().isEmpty());
        assertTrue(config.testAnnotationProcessors().isEmpty());
        assertTrue(config.managedTestAnnotationProcessors().isEmpty());
        assertEquals("src/main/java", config.build().source());
        assertEquals(List.of("src/test/java"), config.build().testSources());
        assertEquals("target/test-classes", config.build().testOutput());
        assertEquals("target/generated/sources/annotations", config.compilerSettings().generatedSources());
        assertEquals("target/generated/test-sources/annotations", config.compilerSettings().generatedTestSources());
        assertEquals("", config.compilerSettings().release());
        assertEquals("", config.compilerSettings().encoding());
        assertTrue(config.compilerSettings().args().isEmpty());
        assertTrue(config.compilerSettings().testArgs().isEmpty());
        assertEquals(PackageMode.THIN, config.packageSettings().mode());
    }


    @Test
    void appliesRepositoryAndBuildDefaultsWhenOptionalSectionsAreMissing() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "tiny"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);

        assertEquals(ProjectConfig.MAVEN_CENTRAL, config.repositories().get("central"));
        assertTrue(config.platforms().isEmpty());
        assertTrue(config.apiDependencies().isEmpty());
        assertTrue(config.managedApiDependencies().isEmpty());
        assertTrue(config.workspaceApiDependencies().isEmpty());
        assertTrue(config.dependencies().isEmpty());
        assertTrue(config.managedDependencies().isEmpty());
        assertTrue(config.annotationProcessors().isEmpty());
        assertTrue(config.managedAnnotationProcessors().isEmpty());
        assertTrue(config.testAnnotationProcessors().isEmpty());
        assertTrue(config.managedTestAnnotationProcessors().isEmpty());
        assertFalse(config.project().main().isPresent());
        assertEquals("src/main/java", config.build().source());
        assertEquals("target/classes", config.build().output());
        assertEquals("target/generated/sources/annotations", config.compilerSettings().generatedSources());
        assertEquals("target/generated/test-sources/annotations", config.compilerSettings().generatedTestSources());
        assertEquals("", config.compilerSettings().release());
        assertEquals("", config.compilerSettings().encoding());
        assertTrue(config.compilerSettings().args().isEmpty());
        assertTrue(config.compilerSettings().testArgs().isEmpty());
        assertEquals(PackageMode.THIN, config.packageSettings().mode());
        assertEquals("", config.nativeSettings().imageName());
        assertEquals("target/native", config.nativeSettings().output());
        assertTrue(config.nativeSettings().args().isEmpty());
    }






    @Test
    void parsesCompilerGeneratedSourceDirectories() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "processor-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [compiler]
                generatedSources = "build/generated/main"
                generatedTestSources = "build/generated/test"
                release = "17"
                encoding = "UTF-8"
                args = ["-Xlint:deprecation", "-parameters"]
                testArgs = ["-Xlint:unchecked"]
                """);

        assertEquals("build/generated/main", config.compilerSettings().generatedSources());
        assertEquals("build/generated/test", config.compilerSettings().generatedTestSources());
        assertEquals("17", config.compilerSettings().release());
        assertEquals("UTF-8", config.compilerSettings().encoding());
        assertEquals(List.of("-Xlint:deprecation", "-parameters"), config.compilerSettings().args());
        assertEquals(List.of("-Xlint:unchecked"), config.compilerSettings().testArgs());
    }


    @Test
    void rejectsCompilerOwnedJavacArgs() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "bad-compiler-args"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [compiler]
                args = ["--release", "17"]
                """));

        assertTrue(exception.getMessage().contains("Zolt owns `--release`"));
        assertTrue(exception.getMessage().contains("[compiler].release"));
    }




    @Test
    void parsesExplicitJavaTestSourceRoots() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [test.sources]
                java = ["src/test/java", "src/integration-test/java"]
                """);

        assertEquals(
                List.of("src/test/java", "src/integration-test/java"),
                config.build().testSources());
    }

    @Test
    void parsesExplicitGroovyTestSourceRoots() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [test.sources]
                java = ["src/test/java"]
                groovy = ["src/test/groovy", "src/integration-test/groovy"]
                """);

        assertEquals(List.of("src/test/java"), config.build().testSources());
        assertEquals(
                List.of("src/test/groovy", "src/integration-test/groovy"),
                config.build().groovyTestSources());
    }


    @Test
    void rejectsMalformedJavaTestSourceRoots() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [test.sources]
                        java = "src/test/java"
                        """));

        assertEquals(
                "Invalid value for [test.sources].java in zolt.toml. Use an array of strings.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownTestSourceLanguage() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [test.sources]
                        kotlin = ["src/test/kotlin"]
                        """));

        assertEquals(
                "Unknown field [test.sources].kotlin in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }


    @Test
    void invalidTomlFailsCleanly() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project
                        name = "broken"
                        """));

        assertTrue(exception.getMessage().contains("Could not parse zolt.toml."));
        assertTrue(exception.getMessage().contains("Fix the TOML syntax"));
    }

    @Test
    void missingRequiredProjectFieldIsActionable() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "missing-version"
                        group = "com.example"
                        java = "21"
                        """));

        assertEquals(
                "Missing required field [project].version in zolt.toml. Add a non-empty string value.",
                exception.getMessage());
    }

    @Test
    void unknownTopLevelSectionFailsCleanly() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [plugins]
                        custom = "nope"
                        """));

        assertEquals(
                "Unknown top-level section [plugins] in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void unknownProjectFieldFailsCleanly() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"
                        packaging = "jar"
                        """));

        assertEquals(
                "Unknown field [project].packaging in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }




}
