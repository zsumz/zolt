package sh.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ZoltTomlPackageFrameworkParserValidationTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

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
                "Unsupported package mode `ear` in zolt.toml. Supported package modes are: thin, spring-boot, war, spring-boot-war, quarkus, uber, bom.",
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
    void rejectsUnknownSpringBootNativeField() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "bad"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [framework.springBoot.native]
                        mode = "maven"
                        """));

        assertEquals(
                "Unknown field [framework.springBoot.native].mode in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }
}
