package sh.zolt.generated;

import static sh.zolt.generated.GeneratedSourceEvidenceServiceTestSupport.openApiSettings;
import static sh.zolt.generated.GeneratedSourceEvidenceServiceTestSupport.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import sh.zolt.project.BuildSettings;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GeneratedSourceEvidenceServiceOpenApiTest {
    @TempDir
    private Path tempDir;

    private final GeneratedSourceEvidenceService service = new GeneratedSourceEvidenceService();

    @Test
    void recordsDeterministicOpenApiToolAndOptionFingerprints() throws IOException {
        Path input = write(tempDir, "src/main/openapi/public-api.yaml", "openapi: 3.1.0\n", 1_000);
        var settings = new sh.zolt.project.OpenApiGenerationSettings(
                java.util.Optional.of("org.openapitools:openapi-generator-cli"),
                java.util.Optional.of("7.11.0"),
                java.util.Optional.of("spring-api"),
                java.util.Optional.of("spring"),
                java.util.Optional.of("spring-boot"),
                java.util.Optional.of("com.example.api"),
                java.util.Optional.of("com.example.api.model"),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Map.of("useTags", "true", "interfaceOnly", "true"),
                java.util.Map.of("generatedAnnotation", "false", "useBeanValidation", "true"),
                java.util.Map.of("dateLibrary", "java8", "useSpringBoot3", "true"),
                java.util.Map.of("models", "", "apis", ""),
                java.util.Map.of("OffsetDateTime", "Instant"),
                java.util.Map.of("Instant", "java.time.Instant"));
        var sameSettingsDifferentMapOrder = new sh.zolt.project.OpenApiGenerationSettings(
                java.util.Optional.of("org.openapitools:openapi-generator-cli"),
                java.util.Optional.of("7.11.0"),
                java.util.Optional.of("spring-api"),
                java.util.Optional.of("spring"),
                java.util.Optional.of("spring-boot"),
                java.util.Optional.of("com.example.api"),
                java.util.Optional.of("com.example.api.model"),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Map.of("interfaceOnly", "true", "useTags", "true"),
                java.util.Map.of("useBeanValidation", "true", "generatedAnnotation", "false"),
                java.util.Map.of("useSpringBoot3", "true", "dateLibrary", "java8"),
                java.util.Map.of("apis", "", "models", ""),
                java.util.Map.of("OffsetDateTime", "Instant"),
                java.util.Map.of("Instant", "java.time.Instant"));

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
        write(tempDir, "src/main/openapi/integration-api.yaml", "openapi: 3.1.0\ninfo:\n  title: Integration\n", 1_000);
        write(tempDir, "src/main/openapi/public-api.yaml", "openapi: 3.1.0\ninfo:\n  title: Public\n", 1_000);
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
}
