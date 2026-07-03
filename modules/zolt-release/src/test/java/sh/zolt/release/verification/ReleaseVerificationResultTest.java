package sh.zolt.release.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ReleaseVerificationResultTest {
    @Test
    void copiesVerifiedArchivesAndExposesImmutableList() {
        ReleaseVerificationResult.VerifiedArchive archive = new ReleaseVerificationResult.VerifiedArchive(
                Path.of("dist/zolt-0.1.0-linux-x64.tar.gz"),
                Path.of("verify/zolt-0.1.0-linux-x64"),
                Path.of("verify/zolt-0.1.0-linux-x64/bin/zolt"));
        List<ReleaseVerificationResult.VerifiedArchive> archives = new ArrayList<>(List.of(archive));

        ReleaseVerificationResult result = new ReleaseVerificationResult(archives);
        archives.clear();

        assertEquals(1, result.verifiedCount());
        assertEquals(List.of(archive), result.archives());
        assertThrows(UnsupportedOperationException.class, () -> result.archives().clear());
    }
}
