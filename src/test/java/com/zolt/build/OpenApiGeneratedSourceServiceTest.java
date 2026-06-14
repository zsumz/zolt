package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.doctor.JdkStatus;
import com.zolt.project.ProjectConfig;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.resolve.ResolvedClasspathPackage;
import com.zolt.resolve.ResolvedPackage;
import com.zolt.toml.ZoltTomlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OpenApiGeneratedSourceServiceTest {
    @TempDir
    private Path projectDir;

    @Test
    void runsOpenApiGeneratorBeforeSourceDiscoveryAndKeepsToolingOutOfApplicationClasspaths() throws IOException {
        writeProjectFiles();
        List<List<String>> commands = new ArrayList<>();
        OpenApiGeneratedSourceService service = service((command, directory) -> {
            commands.add(command);
            writeGeneratedInterface(command);
            return new OpenApiGeneratedSourceService.ProcessResult(0, "generated\n");
        });

        service.generateMain(projectDir, config(), packages());

        assertEquals(1, commands.size());
        List<String> command = commands.getFirst();
        assertEquals(fakeJava().toString(), command.getFirst());
        assertTrue(command.contains("org.openapitools.codegen.OpenAPIGenerator"));
        assertTrue(command.contains("--input-spec"));
        assertTrue(command.contains(projectDir.resolve("src/main/openapi/public-api.yaml").toString()));
        assertTrue(command.contains("--generator-name"));
        assertTrue(command.contains("spring"));
        assertTrue(command.contains("--additional-properties"));
        assertTrue(command.get(command.indexOf("--additional-properties") + 1).contains("sourceFolder=."));
        assertFalse(command.get(command.indexOf("-cp") + 1).contains("app-1.0.0.jar"));
        assertTrue(Files.exists(projectDir.resolve(
                "target/generated/sources/openapi/public-api/com/example/generated/PublicApi.java")));
        assertTrue(Files.exists(projectDir.resolve(
                "target/generated/sources/openapi/public-api/.zolt-openapi-main-public-api.fingerprint")));
        assertTrue(Files.exists(projectDir.resolve(
                "target/generated/sources/openapi/public-api/.zolt-openapi-main-public-api.log")));
    }

    @Test
    void skipsGenerationWhenFingerprintIsCurrent() throws IOException {
        writeProjectFiles();
        List<List<String>> commands = new ArrayList<>();
        OpenApiGeneratedSourceService service = service((command, directory) -> {
            commands.add(command);
            writeGeneratedInterface(command);
            return new OpenApiGeneratedSourceService.ProcessResult(0, "generated\n");
        });

        service.generateMain(projectDir, config(), packages());
        service.generateMain(projectDir, config(), packages());

        assertEquals(1, commands.size());
    }

    @Test
    void generatorFailureIsActionableAndPreservesLog() throws IOException {
        writeProjectFiles();
        OpenApiGeneratedSourceService service = service((command, directory) ->
                new OpenApiGeneratedSourceService.ProcessResult(17, "bad spec\n"));

        BuildException exception = assertThrows(
                BuildException.class,
                () -> service.generateMain(projectDir, config(), packages()));

        assertTrue(exception.getMessage().contains("OpenAPI generation failed for [generated.main.public-api]"));
        assertTrue(exception.getMessage().contains("exit code 17"));
        assertTrue(exception.getMessage().contains("bad spec"));
        assertTrue(Files.readString(projectDir.resolve(
                "target/generated/sources/openapi/public-api/.zolt-openapi-main-public-api.log")).contains("bad spec"));
    }

    @Test
    void runsMultipleSpecsWithSharedPresetAndDeterministicOptionArguments() throws IOException {
        writeProjectFiles();
        Files.writeString(projectDir.resolve("src/main/openapi/internal-api.yaml"), "openapi: 3.1.0\n");
        List<List<String>> commands = new ArrayList<>();
        OpenApiGeneratedSourceService service = service((command, directory) -> {
            commands.add(command);
            writeGeneratedInterface(command);
            return new OpenApiGeneratedSourceService.ProcessResult(0, "generated\n");
        });

        service.generateMain(projectDir, multiSpecConfig(), packages());

        assertEquals(2, commands.size());
        assertTrue(commands.stream().anyMatch(command -> command.contains(projectDir.resolve(
                "src/main/openapi/public-api.yaml").toString())));
        assertTrue(commands.stream().anyMatch(command -> command.contains(projectDir.resolve(
                "src/main/openapi/internal-api.yaml").toString())));

        List<String> publicCommand = commands.stream()
                .filter(command -> command.contains(projectDir.resolve("src/main/openapi/public-api.yaml").toString()))
                .findFirst()
                .orElseThrow();
        assertTrue(publicCommand.contains("--skip-validate-spec"));
        assertEquals(
                "additionalModelTypeAnnotations=@lombok.Builder;@lombok.NoArgsConstructor,interfaceOnly=true,sourceFolder=.,useSpringBoot3=true",
                optionValue(publicCommand, "--additional-properties"));
        assertEquals("apis=true,models=true", optionValue(publicCommand, "--global-property"));
        assertEquals("OffsetDateTime=Instant,UUID=String", optionValue(publicCommand, "--type-mappings"));
        assertEquals("Instant=java.time.Instant,String=java.lang.String", optionValue(publicCommand, "--import-mappings"));
        assertTrue(publicCommand.contains("--api-package"));
        assertTrue(publicCommand.contains("com.example.publicapi"));
        assertTrue(publicCommand.contains("--model-package"));
        assertTrue(publicCommand.contains("com.example.publicmodel"));
    }

    @Test
    void regeneratesWhenSpecOrOptionsChange() throws IOException {
        writeProjectFiles();
        List<List<String>> commands = new ArrayList<>();
        OpenApiGeneratedSourceService service = service((command, directory) -> {
            commands.add(command);
            writeGeneratedInterface(command);
            return new OpenApiGeneratedSourceService.ProcessResult(0, "generated\n");
        });

        service.generateMain(projectDir, configWithAdditionalProperties(Map.of("sourceFolder", ".")), packages());
        service.generateMain(projectDir, configWithAdditionalProperties(Map.of("sourceFolder", ".")), packages());
        Files.writeString(projectDir.resolve("src/main/openapi/public-api.yaml"), "openapi: 3.1.0\ninfo:\n  title: Changed\n");
        service.generateMain(projectDir, configWithAdditionalProperties(Map.of("sourceFolder", ".")), packages());
        service.generateMain(
                projectDir,
                configWithAdditionalProperties(Map.of("sourceFolder", ".", "interfaceOnly", "true")),
                packages());

        assertEquals(3, commands.size());
    }

    private void writeProjectFiles() throws IOException {
        Files.createDirectories(projectDir.resolve("src/main/openapi"));
        Files.writeString(projectDir.resolve("src/main/openapi/public-api.yaml"), "openapi: 3.1.0\n");
        Files.createDirectories(projectDir.resolve("cache/org/openapitools/openapi-generator-cli/7.11.0"));
        Files.writeString(
                projectDir.resolve("cache/org/openapitools/openapi-generator-cli/7.11.0/openapi-generator-cli-7.11.0.jar"),
                "tool\n");
        Files.createDirectories(projectDir.resolve("cache/com/example/app/1.0.0"));
        Files.writeString(projectDir.resolve("cache/com/example/app/1.0.0/app-1.0.0.jar"), "app\n");
    }

    private static void writeGeneratedInterface(List<String> command) {
        Path output = Path.of(command.get(command.indexOf("--output") + 1));
        try {
            Files.createDirectories(output.resolve("com/example/generated"));
            Files.writeString(output.resolve("com/example/generated/PublicApi.java"), """
                    package com.example.generated;

                    public interface PublicApi {
                    }
                    """);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private OpenApiGeneratedSourceService service(OpenApiGeneratedSourceService.ProcessRunner runner) {
        return new OpenApiGeneratedSourceService(
                requiredVersion -> new JdkStatus(
                        Optional.empty(),
                        Optional.of(fakeJava()),
                        Optional.of(Path.of("javac")),
                        Optional.of(Path.of("jar")),
                        Optional.of(requiredVersion),
                        requiredVersion),
                ":",
                runner);
    }

    private Path fakeJava() {
        return projectDir.resolve("fake-java");
    }

    private ProjectConfig config() {
        return configWithAdditionalProperties(Map.of("sourceFolder", "."));
    }

    private ProjectConfig configWithAdditionalProperties(Map<String, String> additionalProperties) {
        StringBuilder properties = new StringBuilder();
        additionalProperties.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (!properties.isEmpty()) {
                        properties.append(", ");
                    }
                    properties.append(entry.getKey()).append(" = \"").append(entry.getValue()).append('"');
                });
        return new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                version = "7.11.0"

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                generator = "spring"
                additionalProperties = { %s }
                """.formatted(properties));
    }

    private ProjectConfig multiSpecConfig() {
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
                validateSpec = false
                additionalProperties = { sourceFolder = ".", interfaceOnly = "true", useSpringBoot3 = "true" }
                configOptions = { additionalModelTypeAnnotations = "@lombok.Builder;@lombok.NoArgsConstructor" }
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

                [generated.main.internal-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/internal-api.yaml"
                output = "target/generated/sources/openapi/internal-api"
                preset = "spring-api"
                apiPackage = "com.example.internalapi"
                modelPackage = "com.example.internalmodel"
                """);
    }

    private static String optionValue(List<String> command, String option) {
        return command.get(command.indexOf(option) + 1);
    }

    private List<ResolvedClasspathPackage> packages() {
        return List.of(
                packageWithScope(
                        "org.openapitools",
                        "openapi-generator-cli",
                        "7.11.0",
                        DependencyScope.TOOL_OPENAPI,
                        "cache/org/openapitools/openapi-generator-cli/7.11.0/openapi-generator-cli-7.11.0.jar"),
                packageWithScope(
                        "com.example",
                        "app",
                        "1.0.0",
                        DependencyScope.COMPILE,
                        "cache/com/example/app/1.0.0/app-1.0.0.jar"));
    }

    private ResolvedClasspathPackage packageWithScope(
            String group,
            String artifact,
            String version,
            DependencyScope scope,
            String jar) {
        return new ResolvedClasspathPackage(
                new ResolvedPackage(
                        new PackageId(group, artifact),
                        version,
                        false,
                        Path.of(""),
                        projectDir.resolve(jar)),
                scope);
    }
}
