package sh.zolt.toml.generated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.ZoltTomlParser;
import org.junit.jupiter.api.Test;

final class ZoltTomlGeneratedSourcesParserValidationTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

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
