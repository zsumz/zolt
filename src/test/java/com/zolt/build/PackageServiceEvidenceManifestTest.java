package com.zolt.build;

import static com.zolt.build.PackageServiceTestSupport.config;
import static com.zolt.build.PackageServiceTestSupport.source;
import static com.zolt.build.PackageServiceTestSupport.writeLockfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.OpenApiGenerationSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.ResourceFilteringSettings;
import com.zolt.project.ResourceMissingTokenPolicy;
import com.zolt.project.ResourceTokenSettings;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageServiceEvidenceManifestTest {
    private final PackageService packageService = new PackageService();

    @TempDir
    private Path projectDir;

    @Test
    void writesDeterministicPackageEvidenceManifestWithoutResourceTokenValues() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        source(projectDir, "src/main/resources/application.properties", "name=@projectName@\nsecret=@secretToken@\n");
        source(projectDir, "src/main/openapi/api.yaml", "openapi: 3.1.0\n");
        source(projectDir, "target/generated/sources/openapi/com/example/generated/GeneratedApi.java", """
                package com.example.generated;

                public interface GeneratedApi {
                }
                """);
        ResourceFilteringSettings filtering = new ResourceFilteringSettings(
                true,
                false,
                List.of("**/*.properties"),
                ResourceMissingTokenPolicy.FAIL,
                Map.of(
                        "projectName", ResourceTokenSettings.project("name"),
                        "secretToken", ResourceTokenSettings.literal("super-secret-value")));
        BuildSettings build = BuildSettings.defaults()
                .withResourceFiltering(filtering)
                .withGeneratedSources(
                        List.of(new GeneratedSourceStep(
                                "openapi",
                                GeneratedSourceKind.DECLARED_ROOT,
                                "java",
                                "target/generated/sources/openapi",
                                List.of("src/main/openapi/api.yaml"),
                                true,
                                false)),
                        List.of());
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withBuildSettings(build);

        PackageResult first = packageService.packageJar(projectDir, config, projectDir.resolve("cache"));
        String firstEvidence = Files.readString(first.evidenceManifestPath().orElseThrow());
        PackageResult second = packageService.packageJar(projectDir, config, projectDir.resolve("cache"));
        String secondEvidence = Files.readString(second.evidenceManifestPath().orElseThrow());

        assertEquals(firstEvidence, secondEvidence);
        assertEquals(
                projectDir.resolve("target/demo-0.1.0.jar.zolt-package.json"),
                first.evidenceManifestPath().orElseThrow());
        assertTrue(firstEvidence.contains("\"schema\": \"zolt.package-evidence.v1\""));
        assertTrue(firstEvidence.contains("\"archive\": \"target/demo-0.1.0.jar\""));
        assertTrue(firstEvidence.contains("\"archiveSha256\": \"sha256:"));
        assertTrue(firstEvidence.contains("\"resourceFiltering\": {"));
        assertTrue(firstEvidence.contains("\"source\": \"literal\""));
        assertTrue(firstEvidence.contains("\"source\": \"project\""));
        assertTrue(firstEvidence.contains("\"path\": \"src/main/resources/application.properties\""));
        assertTrue(firstEvidence.contains("\"generatedSources\": ["));
        assertTrue(firstEvidence.contains("\"id\": \"generated-main-openapi\""));
        assertTrue(firstEvidence.contains("\"path\": \"src/main/openapi/api.yaml\""));
        assertTrue(firstEvidence.contains("\"freshness\": \"fresh\""));
        assertTrue(firstEvidence.contains("\"toolVersionRef\": null"));
        assertFalse(firstEvidence.contains("super-secret-value"));
    }

    @Test
    void packageEvidenceRejectsUnsafeResourceRoot() throws IOException {
        Path classes = projectDir.resolve("target/classes");
        Path archive = projectDir.resolve("target/demo-0.1.0.jar");
        Files.createDirectories(archive.getParent());
        Files.writeString(archive, "archive");
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java"),
                        List.of(),
                        List.of("../outside-resources"),
                        List.of("src/test/resources"),
                        null));
        BuildResult buildResult = new BuildResult(Optional.empty(), 0, 0, classes, "");
        PackagePlan plan = new PackagePlan(
                projectDir,
                PackageMode.THIN,
                archive,
                classes,
                "classes-root",
                Optional.empty(),
                List.of(),
                List.of());
        PackageResult result = new PackageResult(
                buildResult,
                PackageMode.THIN,
                archive,
                Optional.empty(),
                Optional.empty(),
                0,
                false,
                List.of());

        PackageException exception = assertThrows(
                PackageException.class,
                () -> new PackageEvidenceManifestWriter().write(projectDir, config, plan, result, List.of()));

        assertTrue(exception.getMessage().contains("[resources].main"), exception.getMessage());
        assertTrue(exception.getMessage().contains("../outside-resources"), exception.getMessage());
    }

    @Test
    void recordsOpenApiToolVersionRefInPackageEvidenceManifest() throws IOException {
        source(projectDir, "src/main/openapi/api.yaml", "openapi: 3.1.0\n");
        Path output = projectDir.resolve("target/generated/sources/openapi/com/example/generated/GeneratedApi.java");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "package com.example.generated; public interface GeneratedApi {}\n");
        Path classes = projectDir.resolve("target/classes");
        Files.createDirectories(classes);
        Path archive = projectDir.resolve("target/demo-0.1.0.jar");
        Files.writeString(archive, "archive", StandardCharsets.UTF_8);

        BuildSettings build = BuildSettings.defaults().withGeneratedSources(
                List.of(new GeneratedSourceStep(
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
                                Map.of()))),
                List.of());
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withBuildSettings(build);
        BuildResult buildResult = new BuildResult(Optional.empty(), 0, 0, classes, "");
        PackagePlan plan = new PackagePlan(
                projectDir,
                PackageMode.THIN,
                archive,
                classes,
                "classes-root",
                Optional.empty(),
                List.of(),
                List.of());
        PackageResult result = new PackageResult(
                buildResult,
                PackageMode.THIN,
                archive,
                Optional.empty(),
                Optional.empty(),
                1,
                true,
                List.of());

        PackageEvidenceManifestWriter writer = new PackageEvidenceManifestWriter();
        Path firstPath = writer.write(projectDir, config, plan, result, List.of());
        String firstEvidence = Files.readString(firstPath);
        Path secondPath = writer.write(projectDir, config, plan, result, List.of());
        String secondEvidence = Files.readString(secondPath);

        assertEquals(firstEvidence, secondEvidence);
        assertTrue(firstEvidence.contains("\"toolArtifact\": \"org.openapitools:openapi-generator-cli:7.11.0\""));
        assertTrue(firstEvidence.contains("\"toolVersionRef\": \"openapi-generator\""));
        assertTrue(firstEvidence.contains("\"toolFingerprint\": \""));
        assertTrue(firstEvidence.contains("\"optionsFingerprint\": \""));
    }
}
