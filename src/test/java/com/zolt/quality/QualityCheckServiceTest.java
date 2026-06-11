package com.zolt.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.workspace.WorkspaceSelectionRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QualityCheckServiceTest {
    @TempDir
    private Path tempDir;

    @Test
    void ciContextRejectsPlaceholderCredentialValuesWithoutPrintingThem() throws IOException {
        Path projectDir = tempDir.resolve("placeholder-credentials");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "placeholder-credentials"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                company = { url = "https://repo.example.test/maven", credentials = "company-artifactory" }

                [repositoryCredentials.company-artifactory]
                usernameEnv = "ARTIFACTORY_USERNAME"
                passwordEnv = "ARTIFACTORY_ACCESS_TOKEN"
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        Map<String, String> environment = Map.of(
                "ARTIFACTORY_USERNAME", "read.only",
                "ARTIFACTORY_ACCESS_TOKEN", "ReadOnly");
        QualityCheckService service = new QualityCheckService(environment::get);

        QualityCheckReport report = service.check(new QualityCheckRequest(
                projectDir,
                tempDir.resolve("cache"),
                false,
                false,
                List.of(QualityCheckService.EXECUTION_CONTEXT),
                QualityCheckContext.CI,
                null,
                false,
                false,
                false,
                WorkspaceSelectionRequest.defaults()));

        assertEquals("error", report.status());
        String output = QualityCheckFormatter.text(report);
        assertTrue(output.contains("error execution-context [repositoryCredentials.company-artifactory] CI context rejects placeholder credential values"));
        assertTrue(output.contains("ARTIFACTORY_USERNAME, ARTIFACTORY_ACCESS_TOKEN"));
        assertFalse(output.contains("read.only"));
        assertFalse(output.contains("ReadOnly"));
    }

    @Test
    void ciContextRejectsPlaceholderPublishCredentialValuesWithoutPrintingThem() throws IOException {
        Path projectDir = tempDir.resolve("placeholder-publish-credentials");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "placeholder-publish-credentials"
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
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        Map<String, String> environment = Map.of(
                "PUBLISH_USERNAME", "dummy",
                "PUBLISH_ACCESS_TOKEN", "read.only");
        QualityCheckService service = new QualityCheckService(environment::get);

        QualityCheckReport report = service.check(new QualityCheckRequest(
                projectDir,
                tempDir.resolve("cache"),
                false,
                false,
                List.of(QualityCheckService.EXECUTION_CONTEXT),
                QualityCheckContext.CI,
                null,
                false,
                false,
                false,
                WorkspaceSelectionRequest.defaults()));

        assertEquals("error", report.status());
        String output = QualityCheckFormatter.text(report);
        assertTrue(output.contains("error execution-context [repositoryCredentials.publish-creds] CI context rejects placeholder credential values"));
        assertTrue(output.contains("PUBLISH_USERNAME, PUBLISH_ACCESS_TOKEN"));
        assertTrue(output.contains("publish repository `company-releases`"));
        assertFalse(output.contains("dummy"));
        assertFalse(output.contains("read.only"));
    }
}
