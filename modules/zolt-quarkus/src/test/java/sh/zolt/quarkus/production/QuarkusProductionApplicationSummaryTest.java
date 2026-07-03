package sh.zolt.quarkus.production;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.QuarkusAugmentationException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class QuarkusProductionApplicationSummaryTest {
    @Test
    void reportsOutputPresenceFromNullablePaths() {
        QuarkusProductionApplicationSummary summary = new QuarkusProductionApplicationSummary(
                "io.quarkus.bootstrap.app.AugmentResult",
                0,
                null,
                null,
                false,
                Path.of("/repo/target/app-runner"));

        assertFalse(summary.hasJar());
        assertTrue(summary.hasNativeImage());
    }

    @Test
    void rejectsMissingResultClass() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> new QuarkusProductionApplicationSummary(" ", 0, null, null, false, null));

        assertTrue(exception.getMessage().contains("requires a result class"));
    }

    @Test
    void rejectsNegativeArtifactResultCount() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> new QuarkusProductionApplicationSummary("result", -1, null, null, false, null));

        assertTrue(exception.getMessage().contains("cannot be negative"));
    }

    @Test
    void rejectsLibraryDirectoryWithoutJarPath() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> new QuarkusProductionApplicationSummary(
                        "result",
                        1,
                        null,
                        Path.of("/repo/target/quarkus-app/lib"),
                        false,
                        null));

        assertTrue(exception.getMessage().contains("library directory requires a jar path"));
    }
}
