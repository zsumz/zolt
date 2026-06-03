package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    }

    @Test
    void parsesTestDependencies() {
        ProjectConfig config = parser.parse(Path.of("examples/junit-basic/zolt.toml"));

        assertEquals("1.11.4", config.testDependencies().get("org.junit.platform:junit-platform-console-standalone"));
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
        assertEquals("", config.nativeSettings().imageName());
        assertEquals("target/native", config.nativeSettings().output());
        assertTrue(config.nativeSettings().args().isEmpty());
    }

    @Test
    void parsesPlatformsAndManagedDependencies() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "spring"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [platforms]
                "org.springframework.boot:spring-boot-dependencies" = "4.0.6"

                [dependencies]
                "org.springframework.boot:spring-boot-starter-webmvc" = {}
                "org.slf4j:slf4j-api" = { version = "2.0.17" }

                [test.dependencies]
                "org.junit.jupiter:junit-jupiter" = {}
                """);

        assertEquals(
                "4.0.6",
                config.platforms().get("org.springframework.boot:spring-boot-dependencies"));
        assertTrue(config.managedDependencies().contains("org.springframework.boot:spring-boot-starter-webmvc"));
        assertEquals("2.0.17", config.dependencies().get("org.slf4j:slf4j-api"));
        assertTrue(config.managedTestDependencies().contains("org.junit.jupiter:junit-jupiter"));
    }

    @Test
    void parsesAnnotationProcessorDeclarations() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "micronaut"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [platforms]
                "io.micronaut.platform:micronaut-platform" = "5.0.0"

                [annotationProcessors]
                "io.micronaut:micronaut-inject-java" = {}
                "org.mapstruct:mapstruct-processor" = { version = "1.6.3" }

                [test.annotationProcessors]
                "io.micronaut:micronaut-inject-java" = {}
                "com.example:test-processor" = "1.0.0"
                """);

        assertTrue(config.managedAnnotationProcessors().contains("io.micronaut:micronaut-inject-java"));
        assertEquals("1.6.3", config.annotationProcessors().get("org.mapstruct:mapstruct-processor"));
        assertTrue(config.managedTestAnnotationProcessors().contains("io.micronaut:micronaut-inject-java"));
        assertEquals("1.0.0", config.testAnnotationProcessors().get("com.example:test-processor"));
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
                """);

        assertEquals("build/generated/main", config.compilerSettings().generatedSources());
        assertEquals("build/generated/test", config.compilerSettings().generatedTestSources());
    }

    @Test
    void rejectsMalformedAnnotationProcessorDeclaration() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [annotationProcessors]
                        "io.micronaut:micronaut-inject-java" = 42
                        """));

        assertEquals(
                "Invalid value for [annotationProcessors].io.micronaut:micronaut-inject-java in zolt.toml. Use a non-empty string version or {} for a platform-managed version.",
                exception.getMessage());
    }

    @Test
    void parsesNativeSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [native]
                imageName = "demo-native"
                output = "target/native-custom"
                args = ["--no-fallback", "--native-image-info"]
                """);

        assertEquals("demo-native", config.nativeSettings().imageName());
        assertEquals("target/native-custom", config.nativeSettings().output());
        assertEquals(
                List.of("--no-fallback", "--native-image-info"),
                config.nativeSettings().args());
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
                        groovy = ["src/test/groovy"]
                        """));

        assertEquals(
                "Unknown field [test.sources].groovy in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void defaultsNativeImageNameWhenNativeSectionIsPresent() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [native]
                output = "target/native-custom"
                """);

        assertEquals("demo", config.nativeSettings().imageName());
        assertEquals("target/native-custom", config.nativeSettings().output());
        assertTrue(config.nativeSettings().args().isEmpty());
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

    @Test
    void unknownNativeFieldFailsCleanly() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [native]
                        script = "native-image.sh"
                        """));

        assertEquals(
                "Unknown field [native].script in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void dependencyValuesMustBeStrings() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [dependencies]
                        "com.google.guava:guava" = 33
                        """));

        assertEquals(
                "Invalid value for [dependencies].com.google.guava:guava in zolt.toml. Use a non-empty string version or {} for a platform-managed version.",
                exception.getMessage());
    }

    @Test
    void dependencyInlineTablesRejectUnknownFields() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [dependencies]
                        "com.google.guava:guava" = { scope = "compile" }
                        """));

        assertEquals(
                "Unknown field [dependencies.com.google.guava:guava].scope in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void nativeArgsMustBeNonEmptyStrings() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [native]
                        args = ["--no-fallback", ""]
                        """));

        assertEquals(
                "Invalid value for [native].args[1] in zolt.toml. Use a non-empty string.",
                exception.getMessage());
    }

    @Test
    void nativeOutputMustBeAString() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [native]
                        output = 123
                        """));

        assertEquals(
                "Invalid value for [native].output in zolt.toml. Use a non-empty string value.",
                exception.getMessage());
    }
}
