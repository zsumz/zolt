package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.project.QuarkusPackageMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ZoltTomlPackageFrameworkParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesPackageSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "boot"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [package]
                mode = "spring-boot"
                sources = true
                javadoc = true
                tests = true

                [package.metadata]
                name = "Demo Library"
                description = "A demo library"
                url = "https://example.com/demo"
                license = "Apache-2.0"
                developers = ["Shawn"]
                scm = "https://example.com/demo.git"
                issues = "https://example.com/demo/issues"

                [package.manifest]
                "Automatic-Module-Name" = "com.example.boot"
                "Bundle-SymbolicName" = "com.example.boot"
                """);

        assertEquals(PackageMode.SPRING_BOOT, config.packageSettings().mode());
        assertTrue(config.packageSettings().sources());
        assertTrue(config.packageSettings().javadoc());
        assertTrue(config.packageSettings().tests());
        assertEquals("Demo Library", config.packageSettings().metadata().name());
        assertEquals("A demo library", config.packageSettings().metadata().description());
        assertEquals("https://example.com/demo", config.packageSettings().metadata().url());
        assertEquals("Apache-2.0", config.packageSettings().metadata().license());
        assertEquals(List.of("Shawn"), config.packageSettings().metadata().developers());
        assertEquals("https://example.com/demo.git", config.packageSettings().metadata().scm());
        assertEquals("https://example.com/demo/issues", config.packageSettings().metadata().issues());
        assertEquals(Map.of(
                "Automatic-Module-Name", "com.example.boot",
                "Bundle-SymbolicName", "com.example.boot"), config.packageSettings().manifestAttributes());
    }

    @Test
    void parsesQuarkusPackageMode() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "quarkus-app"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [package]
                mode = "quarkus"
                """);

        assertEquals(PackageMode.QUARKUS, config.packageSettings().mode());
    }

    @Test
    void parsesWarPackageModes() {
        ProjectConfig war = parser.parse("""
                [project]
                name = "webapp"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [package]
                mode = "war"
                """);
        ProjectConfig springBootWar = parser.parse("""
                [project]
                name = "boot-webapp"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [package]
                mode = "spring-boot-war"
                """);

        assertEquals(PackageMode.WAR, war.packageSettings().mode());
        assertEquals(PackageMode.SPRING_BOOT_WAR, springBootWar.packageSettings().mode());
    }

    @Test
    void parsesQuarkusFrameworkSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "quarkus-app"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [framework.quarkus]
                enabled = true
                package = "fast-jar"
                """);

        assertTrue(config.frameworkSettings().quarkus().enabled());
        assertEquals(QuarkusPackageMode.FAST_JAR, config.frameworkSettings().quarkus().packageMode());
    }

    @Test
    void defaultsQuarkusFrameworkSettingsWhenOmitted() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "plain-app"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);

        assertFalse(config.frameworkSettings().quarkus().enabled());
        assertEquals(QuarkusPackageMode.FAST_JAR, config.frameworkSettings().quarkus().packageMode());
    }

    @Test
    void parsesBuildMetadataSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "boot"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [build.metadata]
                buildInfo = true
                git = true
                reproducible = true
                """);

        assertTrue(config.build().metadata().buildInfo());
        assertTrue(config.build().metadata().git());
        assertTrue(config.build().metadata().reproducible());
    }

    @Test
    void rejectsMalformedBuildMetadataSetting() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [build.metadata]
                        buildInfo = "yes"
                        """));

        assertEquals(
                "Invalid value for [build.metadata].buildInfo in zolt.toml. Use true or false.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownPackageMode() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [package]
                        mode = "ear"
                        """));

        assertEquals(
                "Unsupported package mode `ear` in zolt.toml. Supported package modes are: thin, spring-boot, war, spring-boot-war, quarkus, uber.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownPackageField() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [package]
                        classifier = "all"
                        """));

        assertEquals(
                "Unknown field [package].classifier in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownQuarkusPackageMode() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [framework.quarkus]
                        package = "legacy-jar"
                        """));

        assertEquals(
                "Unsupported Quarkus package mode `legacy-jar` in zolt.toml. Supported Quarkus package modes are: fast-jar.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownQuarkusFrameworkField() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [framework.quarkus]
                        devMode = true
                        """));

        assertEquals(
                "Unknown field [framework.quarkus].devMode in zolt.toml. Remove it or check the spelling.",
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
