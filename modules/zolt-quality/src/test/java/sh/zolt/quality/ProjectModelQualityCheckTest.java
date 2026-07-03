package sh.zolt.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProjectModelQualityCheckTest extends QualityCheckServiceTestSupport {
    private final ProjectModelQualityCheck check = new ProjectModelQualityCheck();

    @TempDir
    private Path tempDir;

    @Test
    void rejectsAbsoluteProjectPathsWithActionableNextStep() throws IOException {
        ProjectConfig config = parseProject(tempDir.resolve("absolute-path"), """

                [build]
                source = "/tmp/source"
                """);

        QualityCheckResult result = check.check(Optional.empty(), tempDir.resolve("absolute-path"), config).getFirst();

        assertEquals(QualityCheckService.PROJECT_MODEL, result.id());
        assertEquals(QualityCheckStatus.FAILED, result.status());
        assertEquals("[build].source", result.subject());
        assertEquals("Path `/tmp/source` must be project-relative and stay inside the project.", result.message());
        assertEquals(
                "Edit zolt.toml to use a relative path such as `src/main/java` or `target/classes`.",
                result.nextStep());
    }

    @Test
    void rejectsParentEscapingGeneratedInputPathsBeforeFilesystemChecks() throws IOException {
        ProjectConfig config = parseProject(tempDir.resolve("escaping-generated-input"), generatedSourceConfig(
                "main",
                "api",
                "target/generated/sources/api",
                "../api.yaml",
                true));

        QualityCheckResult result = check.check(Optional.empty(), tempDir.resolve("escaping-generated-input"), config).getFirst();

        assertEquals(QualityCheckStatus.FAILED, result.status());
        assertEquals("[generated.main].api.inputs[0]", result.subject());
        assertEquals("Path `../api.yaml` must be project-relative and stay inside the project.", result.message());
    }

    @Test
    void rejectsNonNumericCompilerRelease() throws IOException {
        ProjectConfig config = parseProject(tempDir.resolve("bad-release"), """

                [compiler]
                release = "latest"
                """);

        QualityCheckResult result = check.check(Optional.empty(), tempDir.resolve("bad-release"), config).getFirst();

        assertEquals(QualityCheckStatus.FAILED, result.status());
        assertEquals("[compiler].release", result.subject());
        assertEquals("Compiler release `latest` must be a Java feature version.", result.message());
        assertEquals("Use a numeric release such as `8`, `11`, `17`, or `21`.", result.nextStep());
    }

    @Test
    void rejectsCompilerReleaseNewerThanProjectJava() throws IOException {
        ProjectConfig config = parseProject(tempDir.resolve("too-new-release"), """

                [compiler]
                release = "22"
                """);

        QualityCheckResult result = check.check(Optional.empty(), tempDir.resolve("too-new-release"), config).getFirst();

        assertEquals(QualityCheckStatus.FAILED, result.status());
        assertEquals("[compiler].release", result.subject());
        assertEquals("Compiler release `22` is newer than [project].java `21`.", result.message());
        assertEquals("Lower [compiler].release or raise [project].java in zolt.toml.", result.nextStep());
    }

    @Test
    void warnsWhenLegacyBuildFilesShareDefaultTargetOutputRoot() throws IOException {
        Path projectDir = tempDir.resolve("legacy-target");
        ProjectConfig config = parseProject(projectDir, "");
        Files.writeString(projectDir.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(projectDir.resolve("pom.xml"), "<project />\n");

        List<QualityCheckResult> results = check.check(Optional.empty(), projectDir, config);

        assertEquals(QualityCheckStatus.PASSED, results.get(0).status());
        QualityCheckResult warning = results.get(1);
        assertEquals(QualityCheckStatus.WARNING, warning.status());
        assertEquals("[build].outputRoot", warning.subject());
        assertTrue(warning.message().contains("Maven or Gradle project files are present (pom.xml, build.gradle)"));
        assertEquals(
                "For side-by-side migration, set [build].outputRoot = \".zolt/build\" in zolt.toml so Zolt-owned outputs stay separate.",
                warning.nextStep());
    }

    @Test
    void reportsUnusedVersionAliasesInSortedOrderWithoutFlaggingReferencedAliases() throws IOException {
        ProjectConfig config = parseProject(tempDir.resolve("version-aliases"), """

                [versions]
                boot = "4.0.6"
                lombok = "1.18.36"
                openapi = "7.11.0"
                test-lombok = "1.18.36"
                tomcat = "10.1.40"
                used = "1.0.0"
                unused-b = "2.0.0"
                unused-a = "3.0.0"

                [platforms]
                "org.springframework.boot:spring-boot-dependencies" = { versionRef = "boot" }

                [dependencies]
                "org.example:lib" = { versionRef = "used" }

                [annotationProcessors]
                "org.projectlombok:lombok" = { versionRef = "lombok" }

                [test.annotationProcessors]
                "org.projectlombok:lombok" = { versionRef = "test-lombok" }

                [dependencyConstraints]
                "org.apache.tomcat.embed:tomcat-embed-core" = { versionRef = "tomcat", kind = "strict" }

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

        List<QualityCheckResult> results = check.check(Optional.empty(), tempDir.resolve("version-aliases"), config);

        assertEquals(List.of(
                        "version-aliases|Project model is valid for Zolt-owned checks at "
                                + tempDir.resolve("version-aliases").toAbsolutePath().normalize()
                                + ".",
                        "[versions].unused-a|Version alias `unused-a` is declared but not referenced by any versionRef.",
                        "[versions].unused-b|Version alias `unused-b` is declared but not referenced by any versionRef."),
                results.stream()
                        .map(result -> result.subject() + "|" + result.message())
                        .toList());
        String rendered = QualityCheckFormatter.text(new QualityCheckReport(tempDir, false, results));
        assertFalse(rendered.contains("[versions].used"));
        assertFalse(rendered.contains("[versions].openapi"));
    }
}
