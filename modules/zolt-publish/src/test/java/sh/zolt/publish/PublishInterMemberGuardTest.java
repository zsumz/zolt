package sh.zolt.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PublishInterMemberGuardTest {
    @Test
    void reportsInterMemberSiblingsAbsentFromThePublishSet() {
        ZoltLockfile memberLock = new ZoltLockfile(
                1,
                List.of(
                        workspacePackage("com.acme", "acme-core", "1.0.0"),
                        external("org.slf4j", "slf4j-api", "2.0.13")),
                List.of());

        List<String> missing = PublishInterMemberGuard.missingSiblings(memberLock, Set.of("com.acme:acme-http"));

        // acme-core is an inter-member dependency and is not in the publish set; slf4j is external and ignored.
        assertEquals(List.of("com.acme:acme-core"), missing);
    }

    @Test
    void reportsNothingWhenEverySiblingIsInThePublishSet() {
        ZoltLockfile memberLock = new ZoltLockfile(
                1,
                List.of(workspacePackage("com.acme", "acme-core", "1.0.0")),
                List.of());

        List<String> missing = PublishInterMemberGuard.missingSiblings(
                memberLock, Set.of("com.acme:acme-http", "com.acme:acme-core"));

        assertTrue(missing.isEmpty());
    }

    private static LockPackage external(String group, String artifact, String version) {
        return new LockPackage(
                new PackageId(group, artifact),
                version,
                "https://repo.maven.apache.org/maven2",
                DependencyScope.COMPILE,
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of());
    }

    private static LockPackage workspacePackage(String group, String artifact, String version) {
        return new LockPackage(
                new PackageId(group, artifact),
                version,
                "workspace",
                DependencyScope.COMPILE,
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(artifact),
                Optional.of("target/classes"),
                List.of());
    }
}
