package sh.zolt.quality.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.ProjectConfig;
import sh.zolt.publish.PublishSettingsReader;
import sh.zolt.quality.QualityCheckContext;
import sh.zolt.quality.QualityCheckResult;
import sh.zolt.toml.ZoltTomlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CredentialQualityCheckTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();

    @TempDir
    private Path tempDir;

    @Test
    void repositoryCredentialCheckRejectsEmbeddedUrlCredentialsWithoutLeakingThem() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                company = "https://repo-user:super-secret@repo.example.test/maven"
                """);
        CredentialQualityCheck check = new CredentialQualityCheck(new PublishSettingsReader(), Map.<String, String>of()::get);

        List<QualityCheckResult> results = check.checkRepositoryCredentials(Optional.empty(), config, QualityCheckContext.CI);

        assertEquals(1, results.size());
        QualityCheckResult result = results.getFirst();
        assertEquals("[repositories.company]", result.subject());
        assertTrue(result.message().contains("CI context rejects embedded credentials in repository `company` URL."));
        assertTrue(result.nextStep().contains("Move credentials to [repositoryCredentials] environment references."));
        assertDoesNotLeakSecret(result);
    }

    @Test
    void publishCredentialCheckRejectsEmbeddedUrlCredentialsWithoutLeakingThem() throws IOException {
        Path projectDir = tempDir.resolve("publish-embedded-credentials");
        Files.createDirectories(projectDir);
        String toml = """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [publish]
                releaseRepository = "company-releases"

                [publish.repositories.company-releases]
                url = "https://publish-user:super-secret@repo.example.test/releases"
                """;
        Files.writeString(projectDir.resolve("zolt.toml"), toml);
        ProjectConfig config = parser.parse(toml);
        CredentialQualityCheck check = new CredentialQualityCheck(new PublishSettingsReader(), Map.<String, String>of()::get);

        List<QualityCheckResult> results = check.checkPublishCredentials(
                Optional.empty(),
                projectDir,
                config,
                QualityCheckContext.CI);

        assertEquals(1, results.size());
        QualityCheckResult result = results.getFirst();
        assertEquals("[publish.repositories.company-releases]", result.subject());
        assertTrue(result.message().contains(
                "CI context rejects embedded credentials in publish repository `company-releases` URL."));
        assertTrue(result.nextStep().contains("Move publish credentials to [repositoryCredentials] environment references."));
        assertDoesNotLeakSecret(result);
    }

    private static void assertDoesNotLeakSecret(QualityCheckResult result) {
        String rendered = result.message() + "\n" + result.nextStep();
        assertFalse(rendered.contains("repo-user"));
        assertFalse(rendered.contains("publish-user"));
        assertFalse(rendered.contains("super-secret"));
    }
}
