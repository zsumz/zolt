package sh.zolt.build.packageevidence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.generated.GeneratedSourceEvidence;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.OpenApiGenerationSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageEvidenceGeneratedSourceWriterTest {
    @TempDir
    private Path projectDir;

    @Test
    void writesGeneratedSourceEvidenceWithFingerprintInputs() throws IOException {
        Path input = projectDir.resolve("src/main/openapi/api.yaml");
        Files.createDirectories(input.getParent());
        Files.writeString(input, "openapi: 3.1.0\n");
        GeneratedSourceStep step = new GeneratedSourceStep(
                "openapi",
                GeneratedSourceKind.OPENAPI,
                "java",
                "target/generated/sources/openapi",
                List.of("src/main/openapi/api.yaml"),
                true,
                false,
                new OpenApiGenerationSettings(
                        Optional.of("org.openapitools:openapi-generator-cli"),
                        Optional.of("7.11.0"),
                        Optional.of("openapi-generator"),
                        Optional.empty(),
                        Optional.of("spring"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of()));
        GeneratedSourceEvidence evidence = new GeneratedSourceEvidence(
                "generated-main-openapi",
                "generated-main-openapi",
                "main",
                step,
                projectDir.resolve("target/generated/sources/openapi"),
                List.of(input),
                true,
                true,
                "fresh",
                "zolt-owned-openapi",
                "main-compile",
                "org.openapitools:openapi-generator-cli:7.11.0",
                "tool-hash",
                "options-hash");

        StringBuilder json = new StringBuilder();
        PackageEvidenceGeneratedSourceWriter.write(json, projectDir, List.of(evidence));
        String generatedSources = json.toString();

        assertTrue(generatedSources.contains("\"generatedSources\": ["));
        assertTrue(generatedSources.contains("\"id\": \"generated-main-openapi\""));
        assertTrue(generatedSources.contains("\"kind\": \"openapi\""));
        assertTrue(generatedSources.contains("\"output\": \"target/generated/sources/openapi\""));
        assertTrue(generatedSources.contains("\"toolVersionRef\": \"openapi-generator\""));
        assertTrue(generatedSources.contains("\"toolFingerprint\": \"tool-hash\""));
        assertTrue(generatedSources.contains("\"optionsFingerprint\": \"options-hash\""));
        assertTrue(generatedSources.contains("\"path\": \"src/main/openapi/api.yaml\""));
        assertTrue(generatedSources.contains("\"sha256\": \"sha256:"));
    }
}
