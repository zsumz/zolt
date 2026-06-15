package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.ProjectConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ZoltTomlGeneratedSourcesParserTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @Test
    void parsesGeneratedSourceSteps() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "declared-root"
                language = "java"
                output = "target/generated/sources/openapi"
                inputs = ["src/main/openapi/api.yaml"]

                [generated.test.fixtures]
                kind = "declared-root"
                language = "java"
                output = "target/generated/test-sources/fixtures"
                inputs = ["src/test/fixtures/schema.json"]
                required = false
                clean = true
                """);

        assertEquals(1, config.build().generatedMainSources().size());
        assertEquals("openapi", config.build().generatedMainSources().getFirst().id());
        assertEquals(GeneratedSourceKind.DECLARED_ROOT, config.build().generatedMainSources().getFirst().kind());
        assertEquals("java", config.build().generatedMainSources().getFirst().language());
        assertEquals("target/generated/sources/openapi", config.build().generatedMainSources().getFirst().output());
        assertEquals(List.of("src/main/openapi/api.yaml"), config.build().generatedMainSources().getFirst().inputs());
        assertTrue(config.build().generatedMainSources().getFirst().required());
        assertFalse(config.build().generatedMainSources().getFirst().clean());
        assertEquals(1, config.build().generatedTestSources().size());
        assertEquals("fixtures", config.build().generatedTestSources().getFirst().id());
        assertFalse(config.build().generatedTestSources().getFirst().required());
        assertTrue(config.build().generatedTestSources().getFirst().clean());
    }

    @Test
    void parsesOpenApiGeneratedSourceStepsWithMergedPresetSettings() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "openapi-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                version = "7.11.0"

                [generated.openapiPresets.spring-api]
                generator = "spring"
                library = "spring-boot"
                apiPackage = "com.example.api"
                modelPackage = "com.example.api.model"
                validateSpec = true
                options = { interfaceOnly = "true", useTags = "true" }
                additionalProperties = { generatedAnnotation = "false" }
                configOptions = { dateLibrary = "java8" }
                typeMappings = { OffsetDateTime = "Instant" }

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                preset = "spring-api"
                modelPackage = "com.example.public.model"
                validateSpec = false
                options = { hideGenerationTimestamp = "true", useTags = "false" }
                additionalProperties = { useBeanValidation = "true" }
                configOptions = { useSpringBoot3 = "true" }
                importMappings = { Instant = "java.time.Instant" }
                """);

        assertEquals(1, config.build().generatedMainSources().size());
        var step = config.build().generatedMainSources().getFirst();
        assertEquals("public-api", step.id());
        assertEquals(GeneratedSourceKind.OPENAPI, step.kind());
        assertEquals(List.of("src/main/openapi/public-api.yaml"), step.inputs());
        assertTrue(step.required());
        assertTrue(step.clean());
        assertEquals("org.openapitools:openapi-generator-cli", step.openApi().toolCoordinate().orElseThrow());
        assertEquals("7.11.0", step.openApi().toolVersion().orElseThrow());
        assertEquals("spring-api", step.openApi().preset().orElseThrow());
        assertEquals("spring", step.openApi().generator().orElseThrow());
        assertEquals("spring-boot", step.openApi().library().orElseThrow());
        assertEquals("com.example.api", step.openApi().apiPackage().orElseThrow());
        assertEquals("com.example.public.model", step.openApi().modelPackage().orElseThrow());
        assertFalse(step.openApi().validateSpec().orElseThrow());
        assertEquals(Map.of(
                "hideGenerationTimestamp", "true",
                "interfaceOnly", "true",
                "useTags", "false"), step.openApi().options());
        assertEquals(Map.of(
                "generatedAnnotation", "false",
                "useBeanValidation", "true"), step.openApi().additionalProperties());
        assertEquals(Map.of(
                "dateLibrary", "java8",
                "useSpringBoot3", "true"), step.openApi().configOptions());
        assertEquals(Map.of("OffsetDateTime", "Instant"), step.openApi().typeMappings());
        assertEquals(Map.of("Instant", "java.time.Instant"), step.openApi().importMappings());
    }

    @Test
    void parsesOpenApiToolVersionRef() {
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

        var openApi = config.build().generatedMainSources().getFirst().openApi();
        assertEquals("org.openapitools:openapi-generator-cli", openApi.toolCoordinate().orElseThrow());
        assertEquals("7.11.0", openApi.toolVersion().orElseThrow());
        assertEquals("openapi", openApi.toolVersionRef().orElseThrow());
    }

    @Test
    void rejectsOpenApiToolVersionAndVersionRefTogether() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> parser.parse("""
                        [project]
                        name = "openapi-demo"
                        version = "0.1.0"
                        group = "com.example"
                        java = "21"

                        [versions]
                        openapi = "7.11.0"

                        [generated.openapiTool]
                        coordinate = "org.openapitools:openapi-generator-cli"
                        version = "7.11.0"
                        versionRef = "openapi"

                        [generated.main.public-api]
                        kind = "openapi"
                        language = "java"
                        input = "src/main/openapi/public-api.yaml"
                        output = "target/generated/sources/openapi/public-api"
                        generator = "spring"
                        """));

        assertEquals(
                "Invalid value for [generated.openapiTool] in zolt.toml. Use either version or versionRef, not both.",
                exception.getMessage());
    }

    @Test
    void parsesMultipleOpenApiGeneratedSourceStepsWithSharedPresetOverrides() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "openapi-multi-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                version = "7.11.0"

                [generated.openapiPresets.spring-api]
                generator = "spring"
                library = "spring-boot"
                apiPackage = "com.example.api"
                modelPackage = "com.example.api.model"
                configOptions = { useSpringBoot3 = "true" }

                [generated.main.integration-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/integration-api.yaml"
                output = "target/generated/sources/openapi/integration-api"
                preset = "spring-api"
                apiPackage = "com.example.integration.api"
                modelPackage = "com.example.integration.model"

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                preset = "spring-api"
                apiPackage = "com.example.public.api"
                modelPackage = "com.example.public.model"
                """);

        assertEquals(List.of("integration-api", "public-api"), config.build().generatedMainSources().stream()
                .map(com.zolt.project.GeneratedSourceStep::id)
                .toList());
        var integration = config.build().generatedMainSources().get(0);
        var publicApi = config.build().generatedMainSources().get(1);
        assertEquals("target/generated/sources/openapi/integration-api", integration.output());
        assertEquals("target/generated/sources/openapi/public-api", publicApi.output());
        assertEquals("com.example.integration.api", integration.openApi().apiPackage().orElseThrow());
        assertEquals("com.example.public.api", publicApi.openApi().apiPackage().orElseThrow());
        assertEquals("com.example.integration.model", integration.openApi().modelPackage().orElseThrow());
        assertEquals("com.example.public.model", publicApi.openApi().modelPackage().orElseThrow());
        assertEquals("spring", integration.openApi().generator().orElseThrow());
        assertEquals("spring", publicApi.openApi().generator().orElseThrow());
        assertEquals(Map.of("useSpringBoot3", "true"), integration.openApi().configOptions());
        assertEquals(Map.of("useSpringBoot3", "true"), publicApi.openApi().configOptions());
    }

    @Test
    void rejectsGeneratedSourceCommands() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "declared-root"
                language = "java"
                output = "target/generated/sources/openapi"
                inputs = ["src/main/openapi/api.yaml"]
                command = "generate"
                """));

        assertTrue(exception.getMessage().contains("Unknown field [generated.main.openapi].command"));
    }

    @Test
    void rejectsInvalidOpenApiOptionMapShapes() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/api.yaml"
                output = "target/generated/sources/openapi"
                configOptions = { useSpringBoot3 = true }
                """));

        assertTrue(exception.getMessage().contains("Invalid value for [generated.main.openapi.configOptions].useSpringBoot3"));
        assertTrue(exception.getMessage().contains("Use a non-empty string value."));
    }

    @Test
    void rejectsInvalidOpenApiValidateSpecValue() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "openapi-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/api.yaml"
                output = "target/generated/sources/openapi"
                validateSpec = "false"
                """));

        assertTrue(exception.getMessage().contains("Invalid value for [generated.main.openapi].validateSpec"));
        assertTrue(exception.getMessage().contains("Use true or false"));
    }

    @Test
    void rejectsOpenApiPostProcessingOptions() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/api.yaml"
                output = "target/generated/sources/openapi"
                options = { enablePostProcessFile = "true" }
                """));

        assertTrue(exception.getMessage().contains(
                "Unsupported OpenAPI generator option [generated.main.openapi.options].enablePostProcessFile"));
        assertTrue(exception.getMessage().contains("Zolt does not run generator post-processing hooks"));
    }

    @Test
    void rejectsOpenApiPresetPostProcessingOptions() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.openapiPresets.spring-api]
                additionalProperties = { postProcessFile = "format.sh" }

                [generated.main.openapi]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/api.yaml"
                output = "target/generated/sources/openapi"
                preset = "spring-api"
                """));

        assertTrue(exception.getMessage().contains(
                "Unsupported OpenAPI generator option [generated.openapiPresets.spring-api.additionalProperties].postProcessFile"));
        assertTrue(exception.getMessage().contains("model the behavior as a Zolt-owned generated-source feature"));
    }

    @Test
    void rejectsUnsupportedGeneratedSourceLanguages() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "declared-root"
                language = "kotlin"
                output = "target/generated/sources/openapi"
                inputs = ["src/main/openapi/api.yaml"]
                """));

        assertTrue(exception.getMessage().contains("Unsupported generated source language `kotlin`"));
    }

    @Test
    void rejectsGeneratedSourceStepsWithoutInputs() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.main.openapi]
                kind = "declared-root"
                language = "java"
                output = "target/generated/sources/openapi"
                inputs = []
                """));

        assertTrue(exception.getMessage().contains("Add at least one project-relative input path"));
    }
}
