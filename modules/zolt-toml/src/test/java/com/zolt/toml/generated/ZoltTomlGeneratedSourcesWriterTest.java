package com.zolt.toml.generated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.OpenApiGenerationSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ZoltTomlGeneratedSourcesWriterTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ZoltTomlWriter writer = new ZoltTomlWriter();

    @Test
    void writesGeneratedSourceStepsWhenConfigured() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(BuildSettings.defaults().withGeneratedSources(
                        List.of(new GeneratedSourceStep(
                                "openapi",
                                GeneratedSourceKind.DECLARED_ROOT,
                                "java",
                                "target/generated/sources/openapi",
                                List.of("src/main/openapi/api.yaml"),
                                true,
                                false)),
                        List.of(new GeneratedSourceStep(
                                "fixtures",
                                GeneratedSourceKind.DECLARED_ROOT,
                                "java",
                                "target/generated/test-sources/fixtures",
                                List.of("src/test/fixtures/schema.json"),
                                false,
                                true))));

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[generated.main.openapi]\n"));
        assertTrue(toml.contains("kind = \"declared-root\""));
        assertTrue(toml.contains("output = \"target/generated/sources/openapi\""));
        assertTrue(toml.contains("inputs = [\"src/main/openapi/api.yaml\"]"));
        assertTrue(toml.contains("[generated.test.fixtures]\n"));
        assertTrue(toml.contains("required = false"));
        assertTrue(toml.contains("clean = true"));
        assertEquals(config.build().generatedMainSources(), parsed.build().generatedMainSources());
        assertEquals(config.build().generatedTestSources(), parsed.build().generatedTestSources());
    }

    @Test
    void writesOpenApiGeneratedSourceStepsWhenConfigured() {
        ProjectConfig config = writer.defaultApplicationConfig("hello", "com.example", "com.example.Main")
                .withBuildSettings(BuildSettings.defaults().withGeneratedSources(
                        List.of(new GeneratedSourceStep(
                                "public-api",
                                GeneratedSourceKind.OPENAPI,
                                "java",
                                "target/generated/sources/openapi/public-api",
                                List.of("src/main/openapi/public-api.yaml"),
                                true,
                                true,
                                new OpenApiGenerationSettings(
                                        Optional.of("org.openapitools:openapi-generator-cli"),
                                        Optional.of("7.11.0"),
                                        Optional.empty(),
                                        Optional.of("spring"),
                                        Optional.of("spring-boot"),
                                        Optional.of("com.example.api"),
                                        Optional.of("com.example.api.model"),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.of(false),
                                        Map.of("interfaceOnly", "true"),
                                        Map.of("useBeanValidation", "true"),
                                        Map.of("dateLibrary", "java8"),
                                        Map.of(),
                                        Map.of("OffsetDateTime", "Instant"),
                                        Map.of()))),
                        List.of()));

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[generated.openapiTool]\n"));
        assertTrue(toml.contains("coordinate = \"org.openapitools:openapi-generator-cli\""));
        assertTrue(toml.contains("version = \"7.11.0\""));
        assertTrue(toml.contains("[generated.main.public-api]\n"));
        assertTrue(toml.contains("kind = \"openapi\""));
        assertTrue(toml.contains("input = \"src/main/openapi/public-api.yaml\""));
        assertFalse(toml.contains("inputs = [\"src/main/openapi/public-api.yaml\"]"));
        assertTrue(toml.contains("validateSpec = false"));
        assertTrue(toml.contains("options = { \"interfaceOnly\" = \"true\" }"));
        assertTrue(toml.contains("additionalProperties = { \"useBeanValidation\" = \"true\" }"));
        assertTrue(toml.contains("configOptions = { \"dateLibrary\" = \"java8\" }"));
        assertEquals(config.build().generatedMainSources(), parsed.build().generatedMainSources());
    }

    @Test
    void writesOpenApiToolVersionRefWhenConfigured() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "openapi-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                openapi = "7.11.0"

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                versionRef = "openapi"

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                generator = "spring"
                """);

        String toml = writer.write(config);
        ProjectConfig parsed = parser.parse(toml);

        assertTrue(toml.contains("[generated.openapiTool]\n"));
        assertTrue(toml.contains("coordinate = \"org.openapitools:openapi-generator-cli\""));
        assertTrue(toml.contains("versionRef = \"openapi\""));
        assertFalse(toml.contains("version = \"7.11.0\""));
        assertEquals(config.versionAliases(), parsed.versionAliases());
        assertEquals(config.build().generatedMainSources(), parsed.build().generatedMainSources());
    }

}
