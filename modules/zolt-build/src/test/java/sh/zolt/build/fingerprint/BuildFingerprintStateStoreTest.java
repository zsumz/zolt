package sh.zolt.build.fingerprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildFingerprintStateStoreTest {
    @TempDir
    private Path tempDir;

    private final BuildFingerprintStateStore store = new BuildFingerprintStateStore();

    @Test
    void writesAndReadsFingerprintStateNextToFingerprint() throws IOException {
        Path fingerprint = tempDir.resolve("target/classes/.zolt-build-main.fingerprint");
        Files.createDirectories(fingerprint.getParent());
        Path source = tempDir.resolve("src/main/java/App.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class App {}\n");
        Map<Path, BuildFingerprintCachedFileHash> state = Map.of(
                source.toAbsolutePath().normalize(),
                BuildFingerprintCachedFileHash.read(source, "abc123"));

        store.writeState(fingerprint, "fingerprint-content", state);

        assertTrue(Files.exists(fingerprint.resolveSibling(".zolt-build-main.fingerprint.state")));
        BuildFingerprintState read = store.readState(fingerprint).orElseThrow();
        assertTrue(read.matchesFingerprint("fingerprint-content"));
        assertEquals("abc123", read.hashIfCurrent(source).orElseThrow());
    }
}
