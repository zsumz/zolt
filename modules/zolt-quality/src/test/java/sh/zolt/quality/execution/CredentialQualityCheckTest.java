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
    void credentialChecksSkipOutsideCiContext() throws IOException {
        Path projectDir = tempDir.resolve("local-context");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                company = { url = "https://repo.example.test/maven", credentials = "company-artifactory" }

                [repositoryCredentials.company-artifactory]
                usernameEnv = "ARTIFACTORY_USERNAME"
                passwordEnv = "ARTIFACTORY_ACCESS_TOKEN"
                """);
        ProjectConfig config = parser.parse(projectDir.resolve("zolt.toml"));
        CredentialQualityCheck check = new CredentialQualityCheck(new PublishSettingsReader(), Map.<String, String>of()::get);

        assertEquals(List.of(), check.checkRepositoryCredentials(Optional.empty(), config, QualityCheckContext.LOCAL));
        assertEquals(List.of(), check.checkPublishCredentials(Optional.empty(), projectDir, config, QualityCheckContext.LOCAL));
        assertEquals(List.of(), check.checkResourceTokens(Optional.empty(), config, QualityCheckContext.LOCAL));
    }

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
    void repositoryCredentialCheckReportsInvalidRepositoryUri() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                company = "https://["
                """);
        CredentialQualityCheck check = new CredentialQualityCheck(new PublishSettingsReader(), Map.<String, String>of()::get);

        QualityCheckResult result = check.checkRepositoryCredentials(
                Optional.of("modules/api"),
                config,
                QualityCheckContext.CI).getFirst();

        assertEquals(Optional.of("modules/api"), result.member());
        assertEquals("[repositories.company]", result.subject());
        assertEquals("Repository `company` URL is not a valid URI.", result.message());
        assertEquals(
                "Edit [repositories.company] to use a Maven-compatible HTTPS URL without embedded credentials.",
                result.nextStep());
    }

    @Test
    void repositoryCredentialCheckReportsMissingEnvironmentVariableByNameOnly() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                company = { url = "https://repo.example.test/maven", credentials = "company-artifactory" }

                [repositoryCredentials.company-artifactory]
                usernameEnv = "ARTIFACTORY_USERNAME"
                passwordEnv = "ARTIFACTORY_ACCESS_TOKEN"
                """);
        CredentialQualityCheck check = new CredentialQualityCheck(
                new PublishSettingsReader(),
                Map.of("ARTIFACTORY_USERNAME", "ci-user")::get);

        QualityCheckResult result = check.checkRepositoryCredentials(
                Optional.empty(),
                config,
                QualityCheckContext.CI).getFirst();

        assertEquals("[repositoryCredentials.company-artifactory]", result.subject());
        assertEquals(
                "CI context requires environment variable ARTIFACTORY_ACCESS_TOKEN for repository `company` credentials `company-artifactory` before resolve/build work starts.",
                result.message());
        assertEquals(
                "Set the named CI secret and rerun `zolt check --context ci`. Secret values are never printed.",
                result.nextStep());
        assertFalse(result.message().contains("ci-user"));
    }

    @Test
    void repositoryCredentialCheckReportsCredentialedRepositorySummary() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                alpha = { url = "https://alpha.example.test/maven", credentials = "alpha-creds" }
                beta = { url = "https://beta.example.test/maven", credentials = "beta-creds" }

                [repositoryCredentials.alpha-creds]
                usernameEnv = "ALPHA_USERNAME"
                passwordEnv = "ALPHA_TOKEN"

                [repositoryCredentials.beta-creds]
                usernameEnv = "BETA_USERNAME"
                passwordEnv = "BETA_TOKEN"
                """);
        CredentialQualityCheck check = new CredentialQualityCheck(new PublishSettingsReader(), Map.of(
                        "ALPHA_USERNAME", "alpha-user",
                        "ALPHA_TOKEN", "alpha-token",
                        "BETA_USERNAME", "beta-user",
                        "BETA_TOKEN", "beta-token")
                ::get);

        QualityCheckResult result = check.checkRepositoryCredentials(
                Optional.empty(),
                config,
                QualityCheckContext.CI).getFirst();

        assertEquals("repository-credentials", result.subject());
        assertEquals("CI credential preflight passed for 2 credentialed repositories.", result.message());
        assertEquals("", result.nextStep());
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

    @Test
    void publishCredentialCheckReportsInvalidRepositoryUri() throws IOException {
        Path projectDir = tempDir.resolve("publish-invalid-uri");
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
                url = "https://["
                """;
        Files.writeString(projectDir.resolve("zolt.toml"), toml);
        ProjectConfig config = parser.parse(toml);
        CredentialQualityCheck check = new CredentialQualityCheck(new PublishSettingsReader(), Map.<String, String>of()::get);

        QualityCheckResult result = check.checkPublishCredentials(
                Optional.empty(),
                projectDir,
                config,
                QualityCheckContext.CI).getFirst();

        assertEquals("[publish.repositories.company-releases]", result.subject());
        assertEquals("Publish repository `company-releases` URL is not a valid URI.", result.message());
        assertEquals(
                "Edit [publish.repositories.company-releases] to use a Maven-compatible HTTPS URL without embedded credentials.",
                result.nextStep());
    }

    @Test
    void publishCredentialCheckReportsCredentialedPublishRepositorySummary() throws IOException {
        Path projectDir = tempDir.resolve("publish-credentials-ok");
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
                url = "https://repo.example.test/releases"
                credentials = "publish-creds"

                [repositoryCredentials.publish-creds]
                usernameEnv = "PUBLISH_USERNAME"
                passwordEnv = "PUBLISH_ACCESS_TOKEN"
                """;
        Files.writeString(projectDir.resolve("zolt.toml"), toml);
        ProjectConfig config = parser.parse(toml);
        CredentialQualityCheck check = new CredentialQualityCheck(new PublishSettingsReader(), Map.of(
                        "PUBLISH_USERNAME", "publisher",
                        "PUBLISH_ACCESS_TOKEN", "token-123")
                ::get);

        QualityCheckResult result = check.checkPublishCredentials(
                Optional.empty(),
                projectDir,
                config,
                QualityCheckContext.CI).getFirst();

        assertEquals("publish-credentials", result.subject());
        assertEquals("CI publish credential preflight passed for 1 credentialed publish repository.", result.message());
        assertEquals("", result.nextStep());
    }

    @Test
    void resourceTokenCheckReportsMissingEnvTokenByNameOnly() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [resources.filtering]
                enabled = true

                [resources.tokens]
                apiToken = { env = "API_TOKEN" }
                literalName = { value = "demo" }
                """);
        CredentialQualityCheck check = new CredentialQualityCheck(new PublishSettingsReader(), Map.<String, String>of()::get);

        QualityCheckResult result = check.checkResourceTokens(
                Optional.empty(),
                config,
                QualityCheckContext.CI).getFirst();

        assertEquals("[resources.tokens.apiToken]", result.subject());
        assertEquals(
                "CI context requires environment variable API_TOKEN for resource token `apiToken` before resource copying.",
                result.message());
        assertEquals(
                "Set the named CI variable or change [resources.tokens].apiToken to an explicit non-secret value/project source. Values are never printed.",
                result.nextStep());
    }

    @Test
    void resourceTokenCheckReportsDeterministicSourceCounts() {
        ProjectConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [resources.filtering]
                enabled = true
                test = true

                [resources.tokens]
                buildNumber = { env = "BUILD_NUMBER" }
                literalName = { value = "demo" }
                projectVersion = { project = "version" }
                """);
        CredentialQualityCheck check = new CredentialQualityCheck(
                new PublishSettingsReader(),
                Map.of("BUILD_NUMBER", "42")::get);

        QualityCheckResult result = check.checkResourceTokens(
                Optional.of("apps/api"),
                config,
                QualityCheckContext.CI).getFirst();

        assertEquals(Optional.of("apps/api"), result.member());
        assertEquals("resource-token-inputs", result.subject());
        assertEquals("CI resource token preflight passed for 3 tokens: env=1, project=1, literal=1.", result.message());
    }

    private static void assertDoesNotLeakSecret(QualityCheckResult result) {
        String rendered = result.message() + "\n" + result.nextStep();
        assertFalse(rendered.contains("repo-user"));
        assertFalse(rendered.contains("publish-user"));
        assertFalse(rendered.contains("super-secret"));
    }
}
