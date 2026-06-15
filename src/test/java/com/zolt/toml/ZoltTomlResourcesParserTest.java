package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.ProjectConfig;
import com.zolt.project.ResourceMissingTokenPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ZoltTomlResourcesParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesExplicitResourceRoots() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [resources]
                main = ["src/main/resources", "target/generated/resources"]
                test = ["src/test/resources", "target/generated/test-resources"]
                """);

        assertEquals(
                List.of("src/main/resources", "target/generated/resources"),
                config.build().resourceRoots());
        assertEquals(
                List.of("src/test/resources", "target/generated/test-resources"),
                config.build().testResourceRoots());
    }

    @Test
    void rejectsMalformedResourceRoots() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [resources]
                        main = "src/main/resources"
                        """));

        assertEquals(
                "Invalid value for [resources].main in zolt.toml. Use an array of strings.",
                exception.getMessage());
    }

    @Test
    void parsesResourceFilteringSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [resources.filtering]
                enabled = true
                test = true
                includes = ["**/*.properties", "**/*.yml"]
                missing = "keep"

                [resources.tokens]
                projectVersion = { project = "version" }
                literalName = { value = "demo-app" }
                ciBuild = { env = "CI_BUILD_NUMBER" }
                """);

        assertTrue(config.build().resourceFiltering().enabled());
        assertTrue(config.build().resourceFiltering().testEnabled());
        assertEquals(List.of("**/*.properties", "**/*.yml"), config.build().resourceFiltering().includes());
        assertEquals(ResourceMissingTokenPolicy.KEEP, config.build().resourceFiltering().missing());
        assertEquals(
                "version",
                config.build().resourceFiltering().tokens().get("projectVersion").project().orElseThrow());
        assertEquals(
                "demo-app",
                config.build().resourceFiltering().tokens().get("literalName").value().orElseThrow());
        assertEquals(
                "CI_BUILD_NUMBER",
                config.build().resourceFiltering().tokens().get("ciBuild").env().orElseThrow());
    }

    @Test
    void rejectsResourceTokenWithMultipleSources() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [resources.tokens]
                        projectVersion = { project = "version", value = "0.1.0" }
                        """));

        assertEquals(
                "Invalid value for [resources.tokens].projectVersion in zolt.toml. Declare exactly one of value, env, or project.",
                exception.getMessage());
    }

    @Test
    void rejectsResourceTokenWithUnsupportedProjectField() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [resources.tokens]
                        artifact = { project = "artifactId" }
                        """));

        assertEquals(
                "Invalid value for [resources.tokens].artifact.project in zolt.toml. Supported project fields are: name, version, group, java, main.",
                exception.getMessage());
    }

    @Test
    void rejectsUnsupportedResourceFilteringMissingPolicy() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [resources.filtering]
                        enabled = true
                        missing = "ignore"
                        """));

        assertTrue(exception.getMessage().contains("Unsupported resource filtering missing-token policy `ignore`"));
        assertTrue(exception.getMessage().contains("fail, keep"));
    }
}
