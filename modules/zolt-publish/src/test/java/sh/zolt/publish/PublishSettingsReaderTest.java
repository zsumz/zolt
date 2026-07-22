package sh.zolt.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.RepositoryCredentialSettings;
import sh.zolt.toml.ZoltConfigException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PublishSettingsReaderTest {
    private final PublishSettingsReader reader = new PublishSettingsReader();

    @Test
    void defaultsWhenPublishSectionIsAbsent() {
        PublishSettings settings = reader.read("""
                [project]
                name = "app"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """, Map.of());

        assertFalse(settings.configured());
        assertEquals("", settings.releaseRepository());
        assertEquals("", settings.snapshotRepository());
        assertEquals(List.of("main"), settings.artifacts());
        assertEquals(Map.of(), settings.repositories());
        assertFalse(settings.signing().enabled());
    }

    @Test
    void parsesSigningSettings() {
        PublishSettings settings = reader.read("""
                [publish]
                releaseRepository = "company-releases"

                [publish.repositories.company-releases]
                url = "https://repo.example.test/releases"

                [publish.signing]
                enabled = true
                keyId = "ABCDEF0123456789"
                passphraseEnv = "ZOLT_SIGNING_PASSPHRASE"
                """, Map.of());

        assertTrue(settings.signing().enabled());
        assertEquals(Optional.of("ABCDEF0123456789"), settings.signing().keyId());
        assertEquals(Optional.of("ZOLT_SIGNING_PASSPHRASE"), settings.signing().passphraseEnv());
    }

    @Test
    void rejectsUnknownSigningKeys() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> reader.read("""
                [publish.signing]
                enabled = true
                secret = "leak"
                """, Map.of()));

        assertTrue(exception.getMessage().contains("Unknown key `secret` in [publish.signing] in zolt.toml."));
    }

    @Test
    void parsesPublishSettingsAndRepositoryCredentials() {
        PublishSettings settings = reader.read("""
                [publish]
                releaseRepository = " company-releases "
                snapshotRepository = "company-snapshots"
                artifacts = [" main ", "sources"]

                [publish.repositories.company-releases]
                url = " https://repo.example.test/releases "
                credentials = " publish-creds "

                [publish.repositories.company-snapshots]
                url = "https://repo.example.test/snapshots"
                """, Map.of(
                "publish-creds",
                RepositoryCredentialSettings.basic("publish-creds", "PUBLISH_USERNAME", "PUBLISH_TOKEN")));

        assertTrue(settings.configured());
        assertEquals("company-releases", settings.releaseRepository());
        assertEquals("company-snapshots", settings.snapshotRepository());
        assertEquals(List.of("main", "sources"), settings.artifacts());

        PublishRepositorySettings releaseRepository = settings.repositories().get("company-releases");
        assertEquals("https://repo.example.test/releases", releaseRepository.url());
        assertEquals(Optional.of("publish-creds"), releaseRepository.credentials());

        PublishRepositorySettings snapshotRepository = settings.repositories().get("company-snapshots");
        assertEquals("https://repo.example.test/snapshots", snapshotRepository.url());
        assertEquals(Optional.empty(), snapshotRepository.credentials());
    }

    @Test
    void rejectsUnknownPublishKeys() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> reader.read("""
                [publish]
                releaseRepository = "company-releases"
                url = "https://repo.example.test/releases"
                """, Map.of()));

        assertTrue(exception.getMessage().contains("Unknown key `url` in [publish] in zolt.toml."));
    }

    @Test
    void rejectsMissingPublishRepositoryReference() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> reader.read("""
                [publish]
                releaseRepository = "company-releases"
                """, Map.of()));

        assertTrue(exception.getMessage().contains(
                "[publish].releaseRepository references publish repository `company-releases`, but [publish.repositories.company-releases] is not defined."));
    }

    @Test
    void rejectsMissingPublishCredentialReference() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> reader.read("""
                [publish]
                releaseRepository = "company-releases"

                [publish.repositories.company-releases]
                url = "https://repo.example.test/releases"
                credentials = "publish-creds"
                """, Map.of()));

        assertTrue(exception.getMessage().contains(
                "Publish repository `company-releases` references credentials `publish-creds`, but [repositoryCredentials.publish-creds] is not defined."));
    }
}
