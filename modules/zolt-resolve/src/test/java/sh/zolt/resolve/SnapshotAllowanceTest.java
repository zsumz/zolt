package sh.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.PackageId;
import sh.zolt.resolve.materialization.RepositoryOverlay;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SnapshotAllowanceTest {
    private static final PackageId MEMBER = new PackageId("com.example", "member");
    private static final PackageId OVERLAID = new PackageId("com.example", "snap");

    @TempDir
    private Path tempDir;

    @Test
    void rejectsNonSnapshotVersionsRegardlessOfMembership() {
        SnapshotAllowance allowance = new SnapshotAllowance(Set.of(MEMBER), List.of());
        assertFalse(allowance.permitsSnapshot(MEMBER, "1.0.0"));
    }

    @Test
    void permitsSnapshotForWorkspaceMemberCoordinate() {
        SnapshotAllowance allowance = new SnapshotAllowance(Set.of(MEMBER), List.of());
        assertTrue(allowance.permitsSnapshot(MEMBER, "1.0.0-SNAPSHOT"));
        assertFalse(allowance.permitsSnapshot(new PackageId("com.other", "thing"), "1.0.0-SNAPSHOT"));
    }

    @Test
    void permitsSnapshotWhenOverlayContainsTheJar() throws IOException {
        Path mavenLocalRoot = writeOverlayJar("com/example/snap/1.0.0-SNAPSHOT/snap-1.0.0-SNAPSHOT.jar");
        SnapshotAllowance allowance =
                new SnapshotAllowance(Set.of(), List.of(RepositoryOverlay.mavenLocal(mavenLocalRoot)));
        assertTrue(allowance.permitsSnapshot(OVERLAID, "1.0.0-SNAPSHOT"));
        assertTrue(allowance.overlaysEnabled());
    }

    @Test
    void rejectsSnapshotWhenOverlayEnabledButJarAbsent() {
        SnapshotAllowance allowance = new SnapshotAllowance(
                Set.of(), List.of(RepositoryOverlay.mavenLocal(tempDir.resolve("empty-m2"))));
        assertFalse(allowance.permitsSnapshot(OVERLAID, "1.0.0-SNAPSHOT"));
        assertTrue(allowance.overlaysEnabled());
    }

    @Test
    void noneRejectsEverySnapshot() {
        assertFalse(SnapshotAllowance.none().permitsSnapshot(MEMBER, "1.0.0-SNAPSHOT"));
        assertFalse(SnapshotAllowance.none().overlaysEnabled());
    }

    @Test
    void remediationNamesOverlayFlagWhenDisabledAndInstallWhenEnabled() {
        SnapshotAllowance disabled = SnapshotAllowance.none();
        String disabledNext = disabled.snapshotRemediation("com.example:snap:1.0.0-SNAPSHOT", "zolt resolve");
        assertTrue(disabledNext.contains("--repository-overlay maven-local"), disabledNext);
        assertTrue(disabledNext.contains("com.example:snap:1.0.0-SNAPSHOT"), disabledNext);

        SnapshotAllowance enabled = new SnapshotAllowance(
                Set.of(), List.of(RepositoryOverlay.mavenLocal(tempDir.resolve("m2"))));
        String enabledNext = enabled.snapshotRemediation("com.example:snap:1.0.0-SNAPSHOT", "zolt resolve");
        assertTrue(enabledNext.contains("enabled maven-local overlay"), enabledNext);
    }

    private Path writeOverlayJar(String relativePath) throws IOException {
        Path root = tempDir.resolve("m2");
        Path jar = root.resolve(relativePath);
        Files.createDirectories(jar.getParent());
        Files.write(jar, new byte[] {1, 2, 3});
        return root;
    }
}
