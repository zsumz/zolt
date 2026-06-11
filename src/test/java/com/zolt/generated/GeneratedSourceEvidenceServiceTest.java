package com.zolt.generated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.OpenApiGenerationSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GeneratedSourceEvidenceServiceTest {
    @TempDir
    private Path tempDir;

    private final GeneratedSourceEvidenceService service = new GeneratedSourceEvidenceService();

    @Test
    void reportsDeterministicLifecycleEvidenceForDeclaredRoots() throws IOException {
        Path freshInput = write("src/main/openapi/api.yaml", "openapi: 3.1.0\n", 1_000);
        Path freshOutput = write("target/generated/sources/openapi/com/example/GeneratedApi.java", """
                package com.example;
                public final class GeneratedApi {}
                """, 2_000);
        Path staleInput = write("src/test/fixtures/schema.json", "{}\n", 4_000);
        Path staleOutput = write("target/generated/test-sources/fixtures/com/example/Fixture.java", """
                package com.example;
                public final class Fixture {}
                """, 3_000);

        BuildSettings build = BuildSettings.defaults().withGeneratedSources(
                List.of(new GeneratedSourceStep(
                        "openapi",
                        GeneratedSourceKind.DECLARED_ROOT,
                        "java",
                        "target/generated/sources/openapi",
                        List.of("src/main/openapi/api.yaml"),
                        true,
                        false)),
                List.of(
                        new GeneratedSourceStep(
                                "fixtures",
                                GeneratedSourceKind.DECLARED_ROOT,
                                "java",
                                "target/generated/test-sources/fixtures",
                                List.of("src/test/fixtures/schema.json"),
                                true,
                                true),
                        new GeneratedSourceStep(
                                "missing-input",
                                GeneratedSourceKind.DECLARED_ROOT,
                                "java",
                                "target/generated/test-sources/missing-input",
                                List.of("src/test/fixtures/missing.json"),
                                true,
                                false)));

        List<GeneratedSourceEvidence> evidence = service.evidence(tempDir, build);

        assertEquals(List.of(
                "generated-main-openapi",
                "generated-test-fixtures",
                "generated-test-missing-input"), evidence.stream().map(GeneratedSourceEvidence::id).toList());
        assertEquals(List.of("fresh", "stale", "input-missing"), evidence.stream().map(GeneratedSourceEvidence::freshness).toList());
        assertEquals(List.of(
                "external-declared-root",
                "zolt-owned-clean",
                "external-declared-root"), evidence.stream().map(GeneratedSourceEvidence::ownership).toList());
        assertEquals(List.of("main-compile", "test-compile", "test-compile"), evidence.stream().map(GeneratedSourceEvidence::compileLane).toList());
        assertEquals(freshInput.toAbsolutePath().normalize(), evidence.get(0).inputs().getFirst());
        assertEquals(freshOutput.getParent().getParent().getParent().toAbsolutePath().normalize(), evidence.get(0).output());
        assertEquals(staleInput.toAbsolutePath().normalize(), evidence.get(1).inputs().getFirst());
        assertEquals(staleOutput.getParent().getParent().getParent().toAbsolutePath().normalize(), evidence.get(1).output());
    }

    @Test
    void recordsDeterministicOpenApiToolAndOptionFingerprints() throws IOException {
        Path input = write("src/main/openapi/public-api.yaml", "openapi: 3.1.0\n", 1_000);
        OpenApiGenerationSettings settings = new OpenApiGenerationSettings(
                Optional.of("org.openapitools:openapi-generator-cli"),
                Optional.of("7.11.0"),
                Optional.of("spring-api"),
                Optional.of("spring"),
                Optional.of("spring-boot"),
                Optional.of("com.example.api"),
                Optional.of("com.example.api.model"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of("useTags", "true", "interfaceOnly", "true"),
                Map.of("generatedAnnotation", "false", "useBeanValidation", "true"),
                Map.of("dateLibrary", "java8", "useSpringBoot3", "true"),
                Map.of("models", "", "apis", ""),
                Map.of("OffsetDateTime", "Instant"),
                Map.of("Instant", "java.time.Instant"));
        OpenApiGenerationSettings sameSettingsDifferentMapOrder = new OpenApiGenerationSettings(
                Optional.of("org.openapitools:openapi-generator-cli"),
                Optional.of("7.11.0"),
                Optional.of("spring-api"),
                Optional.of("spring"),
                Optional.of("spring-boot"),
                Optional.of("com.example.api"),
                Optional.of("com.example.api.model"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of("interfaceOnly", "true", "useTags", "true"),
                Map.of("useBeanValidation", "true", "generatedAnnotation", "false"),
                Map.of("useSpringBoot3", "true", "dateLibrary", "java8"),
                Map.of("apis", "", "models", ""),
                Map.of("OffsetDateTime", "Instant"),
                Map.of("Instant", "java.time.Instant"));

        BuildSettings build = BuildSettings.defaults().withGeneratedSources(
                List.of(
                        new GeneratedSourceStep(
                                "public-api",
                                GeneratedSourceKind.OPENAPI,
                                "java",
                                "target/generated/sources/openapi/public-api",
                                List.of("src/main/openapi/public-api.yaml"),
                                true,
                                true,
                                settings),
                        new GeneratedSourceStep(
                                "same-public-api",
                                GeneratedSourceKind.OPENAPI,
                                "java",
                                "target/generated/sources/openapi/same-public-api",
                                List.of("src/main/openapi/public-api.yaml"),
                                true,
                                true,
                                sameSettingsDifferentMapOrder)),
                List.of());

        List<GeneratedSourceEvidence> evidence = service.evidence(tempDir, build);

        assertEquals(input.toAbsolutePath().normalize(), evidence.getFirst().inputs().getFirst());
        assertEquals(List.of("zolt-owned-openapi", "zolt-owned-openapi"), evidence.stream().map(GeneratedSourceEvidence::ownership).toList());
        assertEquals(List.of(
                "org.openapitools:openapi-generator-cli:7.11.0",
                "org.openapitools:openapi-generator-cli:7.11.0"), evidence.stream().map(GeneratedSourceEvidence::toolArtifact).toList());
        assertEquals(64, evidence.getFirst().toolFingerprint().length());
        assertEquals(64, evidence.getFirst().optionsFingerprint().length());
        assertEquals(evidence.getFirst().toolFingerprint(), evidence.get(1).toolFingerprint());
        assertEquals(evidence.getFirst().optionsFingerprint(), evidence.get(1).optionsFingerprint());
    }

    @Test
    void recordsDistinctOpenApiFingerprintsForIndependentStepOverrides() throws IOException {
        write("src/main/openapi/integration-api.yaml", "openapi: 3.1.0\ninfo:\n  title: Integration\n", 1_000);
        write("src/main/openapi/public-api.yaml", "openapi: 3.1.0\ninfo:\n  title: Public\n", 1_000);
        BuildSettings build = BuildSettings.defaults().withGeneratedSources(
                List.of(
                        new GeneratedSourceStep(
                                "integration-api",
                                GeneratedSourceKind.OPENAPI,
                                "java",
                                "target/generated/sources/openapi/integration-api",
                                List.of("src/main/openapi/integration-api.yaml"),
                                true,
                                true,
                                openApiSettings("com.example.integration.api", "com.example.integration.model")),
                        new GeneratedSourceStep(
                                "public-api",
                                GeneratedSourceKind.OPENAPI,
                                "java",
                                "target/generated/sources/openapi/public-api",
                                List.of("src/main/openapi/public-api.yaml"),
                                true,
                                true,
                                openApiSettings("com.example.public.api", "com.example.public.model"))),
                List.of());

        List<GeneratedSourceEvidence> evidence = service.evidence(tempDir, build);

        assertEquals(List.of(
                "target/generated/sources/openapi/integration-api",
                "target/generated/sources/openapi/public-api"), evidence.stream()
                        .map(item -> tempDir.toAbsolutePath().normalize().relativize(item.output()).toString())
                        .toList());
        assertEquals(evidence.getFirst().toolFingerprint(), evidence.get(1).toolFingerprint());
        assertNotEquals(evidence.getFirst().optionsFingerprint(), evidence.get(1).optionsFingerprint());
    }

    private static OpenApiGenerationSettings openApiSettings(String apiPackage, String modelPackage) {
        return new OpenApiGenerationSettings(
                Optional.of("org.openapitools:openapi-generator-cli"),
                Optional.of("7.11.0"),
                Optional.of("spring-api"),
                Optional.of("spring"),
                Optional.of("spring-boot"),
                Optional.of(apiPackage),
                Optional.of(modelPackage),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of("interfaceOnly", "true"),
                Map.of(),
                Map.of("useSpringBoot3", "true"),
                Map.of("models", "", "apis", ""),
                Map.of(),
                Map.of());
    }

    private Path write(String relativePath, String content, long modifiedMillis) throws IOException {
        Path path = tempDir.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        Files.setLastModifiedTime(path, FileTime.fromMillis(modifiedMillis));
        return path;
    }
}
