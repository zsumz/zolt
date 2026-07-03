package sh.zolt.quality.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import sh.zolt.project.ProjectConfig;
import sh.zolt.quality.QualityCheckResult;
import sh.zolt.quality.QualityCheckService;
import sh.zolt.quality.QualityCheckStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageMetadataQualityCheckTest extends PackageQualityCheckTestSupport {
    @TempDir
    private Path tempDir;

    @Test
    void metadataPassesWhenNoLibraryPackageProfileIsRequested() throws IOException {
        Path projectDir = tempDir.resolve("plain-app");
        ProjectConfig config = parseProject(projectDir, "");

        QualityCheckResult result = check.checkMetadata(Optional.empty(), projectDir, config);

        assertResult(
                result,
                QualityCheckService.PACKAGE_METADATA,
                QualityCheckStatus.PASSED,
                "plain-app",
                "No library package metadata is requested.",
                "");
    }

    @Test
    void metadataRequiresSourcesJarWhenPublicationMetadataIsEnabled() throws IOException {
        Path projectDir = tempDir.resolve("missing-sources");
        ProjectConfig config = parseProject(projectDir, packageMetadata());

        QualityCheckResult result = check.checkMetadata(Optional.empty(), projectDir, config);

        assertResult(
                result,
                QualityCheckService.PACKAGE_METADATA,
                QualityCheckStatus.FAILED,
                "[package].sources",
                "Library package metadata is enabled, but sources jar generation is disabled.",
                "Set [package].sources = true for library projects.");
    }

    @Test
    void metadataRequiresJavadocWhenMainSourcesExist() throws IOException {
        Path projectDir = tempDir.resolve("missing-javadoc");
        Files.createDirectories(projectDir.resolve("src/main/java/com/example"));
        Files.writeString(projectDir.resolve("src/main/java/com/example/Api.java"), "package com.example; public interface Api {}\n");
        ProjectConfig config = parseProject(projectDir, """

                [package]
                sources = true
                """
                + packageMetadata());

        QualityCheckResult result = check.checkMetadata(Optional.empty(), projectDir, config);

        assertResult(
                result,
                QualityCheckService.PACKAGE_METADATA,
                QualityCheckStatus.FAILED,
                "[package].javadoc",
                "Library package metadata is enabled, but javadoc jar generation is disabled.",
                "Set [package].javadoc = true when publishing Java APIs.");
    }

    @Test
    void metadataRequiresTestsJarWhenTestSourcesExist() throws IOException {
        Path projectDir = tempDir.resolve("missing-tests");
        Files.createDirectories(projectDir.resolve("src/test/java/com/example"));
        Files.writeString(projectDir.resolve("src/test/java/com/example/ApiTest.java"), "package com.example; final class ApiTest {}\n");
        ProjectConfig config = parseProject(projectDir, """

                [package]
                sources = true
                javadoc = true
                """
                + packageMetadata());

        QualityCheckResult result = check.checkMetadata(Optional.empty(), projectDir, config);

        assertResult(
                result,
                QualityCheckService.PACKAGE_METADATA,
                QualityCheckStatus.FAILED,
                "[package].tests",
                "Test sources are present, but tests jar generation is disabled for this library package.",
                "Set [package].tests = true or remove test sources from the library artifact story.");
    }

    @Test
    void metadataReportsFirstMissingPublicationField() throws IOException {
        Path projectDir = tempDir.resolve("missing-publication-field");
        ProjectConfig config = parseProject(projectDir, """

                [package]
                sources = true
                javadoc = true
                tests = true
                """);

        QualityCheckResult result = check.checkMetadata(Optional.empty(), projectDir, config);

        assertResult(
                result,
                QualityCheckService.PACKAGE_METADATA,
                QualityCheckStatus.FAILED,
                "[package.metadata].name",
                "Library package metadata is enabled, but publication metadata field `name` is missing.",
                "Fill [package.metadata].name in zolt.toml.");
    }

    @Test
    void metadataReportsLaterMissingPublicationFieldsWithTargetedNextSteps() throws IOException {
        List<MissingMetadataCase> cases = List.of(
                new MissingMetadataCase("description", """
                        name = "Example Library"
                        """),
                new MissingMetadataCase("url", """
                        name = "Example Library"
                        description = "Small Java helpers."
                        """),
                new MissingMetadataCase("license", """
                        name = "Example Library"
                        description = "Small Java helpers."
                        url = "https://example.com/library"
                        """),
                new MissingMetadataCase("developers", """
                        name = "Example Library"
                        description = "Small Java helpers."
                        url = "https://example.com/library"
                        license = "Apache-2.0"
                        """),
                new MissingMetadataCase("scm", """
                        name = "Example Library"
                        description = "Small Java helpers."
                        url = "https://example.com/library"
                        license = "Apache-2.0"
                        developers = ["Example Team"]
                        """),
                new MissingMetadataCase("issues", """
                        name = "Example Library"
                        description = "Small Java helpers."
                        url = "https://example.com/library"
                        license = "Apache-2.0"
                        developers = ["Example Team"]
                        scm = "https://example.com/library.git"
                        """));

        for (MissingMetadataCase testCase : cases) {
            Path projectDir = tempDir.resolve("missing-" + testCase.field());
            ProjectConfig config = parseProject(projectDir, """

                    [package]
                    sources = true
                    javadoc = true
                    tests = true

                    [package.metadata]
                    """
                    + testCase.metadata());

            QualityCheckResult result = check.checkMetadata(Optional.empty(), projectDir, config);

            assertResult(
                    result,
                    QualityCheckService.PACKAGE_METADATA,
                    QualityCheckStatus.FAILED,
                    "[package.metadata]." + testCase.field(),
                    "Library package metadata is enabled, but publication metadata field `" + testCase.field() + "` is missing.",
                    "Fill [package.metadata]." + testCase.field() + " in zolt.toml.");
        }
    }

    @Test
    void publicationMetadataAloneRequestsLibraryPackageProfile() throws IOException {
        List<String> metadataBodies = List.of(
                "description = \"Small Java helpers.\"\n",
                "url = \"https://example.com/library\"\n",
                "license = \"Apache-2.0\"\n",
                "developers = [\"Example Team\"]\n",
                "scm = \"https://example.com/library.git\"\n",
                "issues = \"https://example.com/library/issues\"\n");

        for (int index = 0; index < metadataBodies.size(); index++) {
            Path projectDir = tempDir.resolve("metadata-profile-" + index);
            ProjectConfig config = parseProject(projectDir, """

                    [package.metadata]
                    """
                    + metadataBodies.get(index));

            QualityCheckResult result = check.checkMetadata(Optional.empty(), projectDir, config);

            assertResult(
                    result,
                    QualityCheckService.PACKAGE_METADATA,
                    QualityCheckStatus.FAILED,
                    "[package].sources",
                    "Library package metadata is enabled, but sources jar generation is disabled.",
                    "Set [package].sources = true for library projects.");
        }
    }

    @Test
    void metadataPassesWhenLibraryMetadataIsComplete() throws IOException {
        Path projectDir = tempDir.resolve("complete-metadata");
        ProjectConfig config = parseProject(projectDir, """

                [package]
                sources = true
                javadoc = true
                tests = true
                """
                + packageMetadata());

        QualityCheckResult result = check.checkMetadata(Optional.of("modules/library"), projectDir, config);

        assertEquals(Optional.of("modules/library"), result.member());
        assertResult(
                result,
                QualityCheckService.PACKAGE_METADATA,
                QualityCheckStatus.PASSED,
                "complete-metadata",
                "Library package metadata is complete.",
                "");
    }

    @Test
    void manifestMetadataPassesWhenNoLibraryProfileIsRequested() throws IOException {
        ProjectConfig config = parseProject(tempDir.resolve("manifest-plain-app"), "");

        QualityCheckResult result = check.checkManifestMetadata(Optional.empty(), config);

        assertResult(
                result,
                QualityCheckService.MANIFEST_METADATA,
                QualityCheckStatus.PASSED,
                "manifest-plain-app",
                "No library manifest metadata is requested.",
                "");
    }

    @Test
    void manifestMetadataRejectsZoltOwnedAttributesCaseInsensitively() throws IOException {
        ProjectConfig config = parseProject(tempDir.resolve("owned-manifest"), """

                [package.manifest]
                "main-class" = "com.example.Main"
                """);

        QualityCheckResult result = check.checkManifestMetadata(Optional.of("modules/api"), config);

        assertEquals(Optional.of("modules/api"), result.member());
        assertResult(
                result,
                QualityCheckService.MANIFEST_METADATA,
                QualityCheckStatus.FAILED,
                "[package.manifest].main-class",
                "Manifest attribute `main-class` is owned by Zolt.",
                "Remove it from [package.manifest]; use [project].main for Main-Class.");
    }

    @Test
    void manifestMetadataRequiresAutomaticModuleNameForLibraryProfiles() throws IOException {
        ProjectConfig config = parseProject(tempDir.resolve("missing-module-name"), """

                [package]
                sources = true
                """
                + packageMetadata());

        QualityCheckResult result = check.checkManifestMetadata(Optional.empty(), config);

        assertResult(
                result,
                QualityCheckService.MANIFEST_METADATA,
                QualityCheckStatus.FAILED,
                "[package.manifest].Automatic-Module-Name",
                "Library package metadata is enabled, but Automatic-Module-Name is missing.",
                "Add [package.manifest].\"Automatic-Module-Name\" with a stable Java module name.");
    }

    @Test
    void manifestMetadataPassesWithCaseInsensitiveAutomaticModuleName() throws IOException {
        ProjectConfig config = parseProject(tempDir.resolve("manifest-ok"), """

                [package]
                sources = true

                [package.manifest]
                "automatic-module-name" = "com.example.library"
                """
                + packageMetadata());

        QualityCheckResult result = check.checkManifestMetadata(Optional.empty(), config);

        assertResult(
                result,
                QualityCheckService.MANIFEST_METADATA,
                QualityCheckStatus.PASSED,
                "manifest-ok",
                "Library manifest metadata is deterministic.",
                "");
    }

    private record MissingMetadataCase(String field, String metadata) {
    }
}
