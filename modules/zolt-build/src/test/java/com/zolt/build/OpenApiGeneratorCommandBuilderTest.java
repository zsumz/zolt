package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class OpenApiGeneratorCommandBuilderTest {
    private static final Path PROJECT_ROOT = Path.of("/workspace/demo").toAbsolutePath().normalize();

    @Test
    void buildsDeterministicOpenApiGeneratorCommand() {
        OpenApiGeneratorCommandBuilder builder = new OpenApiGeneratorCommandBuilder(":");
        GeneratedSourceStep step = config().build().generatedMainSources().getFirst();

        List<String> command = builder.command(
                PROJECT_ROOT,
                Path.of("/jdk/bin/java"),
                List.of(
                        PROJECT_ROOT.resolve("cache/openapi-generator-b.jar"),
                        PROJECT_ROOT.resolve("cache/openapi-generator-a.jar")),
                "main",
                step);

        assertEquals("/jdk/bin/java", command.getFirst());
        assertEquals(
                PROJECT_ROOT.resolve("cache/openapi-generator-a.jar")
                        + ":"
                        + PROJECT_ROOT.resolve("cache/openapi-generator-b.jar"),
                optionValue(command, "-cp"));
        assertTrue(command.contains("org.openapitools.codegen.OpenAPIGenerator"));
        assertTrue(command.contains("generate"));
        assertEquals(
                PROJECT_ROOT.resolve("src/main/openapi/public-api.yaml").toString(),
                optionValue(command, "--input-spec"));
        assertEquals(
                PROJECT_ROOT.resolve("target/generated/sources/openapi/public-api").toString(),
                optionValue(command, "--output"));
        assertEquals("spring", optionValue(command, "--generator-name"));
        assertEquals("spring-boot", optionValue(command, "--library"));
        assertEquals("com.example.publicapi", optionValue(command, "--api-package"));
        assertEquals("com.example.publicmodel", optionValue(command, "--model-package"));
        assertEquals("com.example.invoker", optionValue(command, "--invoker-package"));
        assertTrue(command.contains("--skip-validate-spec"));
        assertEquals(
                "additionalModelTypeAnnotations=@lombok.Builder,interfaceOnly=true,sourceFolder=.",
                optionValue(command, "--additional-properties"));
        assertEquals("apis=true,models=true", optionValue(command, "--global-property"));
        assertEquals("OffsetDateTime=Instant,UUID=String", optionValue(command, "--type-mappings"));
        assertEquals("Instant=java.time.Instant,String=java.lang.String", optionValue(command, "--import-mappings"));
    }

    private static ProjectConfig config() {
        return new ZoltTomlParser().parse("""
                [project]
                name = "demo"
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
                modelPackage = "com.example.model"
                invokerPackage = "com.example.invoker"
                validateSpec = false
                additionalProperties = { sourceFolder = ".", interfaceOnly = "true" }
                configOptions = { additionalModelTypeAnnotations = "@lombok.Builder" }
                globalProperties = { models = "true", apis = "true" }
                typeMappings = { UUID = "String", OffsetDateTime = "Instant" }
                importMappings = { String = "java.lang.String", Instant = "java.time.Instant" }

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                preset = "spring-api"
                apiPackage = "com.example.publicapi"
                modelPackage = "com.example.publicmodel"
                """);
    }

    private static String optionValue(List<String> command, String option) {
        return command.get(command.indexOf(option) + 1);
    }
}
