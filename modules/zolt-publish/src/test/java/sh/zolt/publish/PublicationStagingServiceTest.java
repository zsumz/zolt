package sh.zolt.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PublicationStagingServiceTest {
    @TempDir
    private Path tempDir;

    @Test
    void freezesPrimaryBytesBeforeTheOriginalCanMutate() throws IOException {
        Path original = tempDir.resolve("demo.jar");
        Files.writeString(original, "original bytes");
        PublicationStagingService service = new PublicationStagingService(name -> null);

        List<StagedPublicationFile> staged = service.stage(
                tempDir.resolve("staging"),
                List.of(new PublicationSource("com/acme/demo/1.0/demo-1.0.jar", original)),
                PublishSigningSettings.disabled());
        Files.writeString(original, "mutated after staging");

        assertEquals("original bytes", Files.readString(staged.getFirst().source()));
        assertEquals(
                PublishChecksum.hex(staged.getFirst().source(), "SHA-256"),
                staged.getFirst().sha256());
        assertEquals(4, staged.size());
    }

    @Test
    void refusesResumeWhenNeitherStagingNorOriginalHasRecordedBytes() throws IOException {
        Path original = tempDir.resolve("demo.pom");
        Files.writeString(original, "changed bytes");
        PublicationStagingService service = new PublicationStagingService(name -> null);

        PublishException exception = assertThrows(
                PublishException.class,
                () -> service.stage(
                        tempDir.resolve("staging"),
                        List.of(new PublicationSource("com/acme/demo/1.0/demo-1.0.pom", original)),
                        PublishSigningSettings.disabled(),
                        new PublicationResume(Map.of(
                                "com/acme/demo/1.0/demo-1.0.pom",
                                "0".repeat(64)))));

        assertTrue(exception.getMessage().contains("current publication input"));
    }

    @Test
    void refusesChangedCurrentInputEvenWhenTheRecordedStagedCopySurvives() throws IOException {
        Path original = tempDir.resolve("demo.pom");
        Files.writeString(original, "original pom");
        Path stagingRoot = tempDir.resolve("staging");
        String repositoryPath = "com/acme/demo/1.0/demo-1.0.pom";
        PublicationStagingService service = new PublicationStagingService(name -> null);
        List<StagedPublicationFile> first = service.stage(
                stagingRoot,
                List.of(new PublicationSource(repositoryPath, original)),
                PublishSigningSettings.disabled());
        Files.writeString(original, "changed generated pom");

        PublishException exception = assertThrows(
                PublishException.class,
                () -> service.stage(
                        stagingRoot,
                        List.of(new PublicationSource(repositoryPath, original)),
                        PublishSigningSettings.disabled(),
                        new PublicationResume(Map.of(repositoryPath, first.getFirst().sha256()))));

        assertTrue(exception.getMessage().contains("current publication input"));
    }

    @Test
    void signingIdentityIncludesTheExactSourceDateEpoch() {
        PublicationStagingService service =
                new PublicationStagingService(Map.of(SourceDateEpoch.ENV_NAME, "1700000123")::get);
        PublishSigningSettings signing =
                new PublishSigningSettings(true, Optional.of("ABCD1234"), Optional.empty());

        assertEquals("key=ABCD1234;sde=1700000123", service.signingIdentity(signing));
    }
}
