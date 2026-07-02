package sh.zolt.workspace.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.resolve.ResolveResult;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WorkspaceResolveServiceVersionSelectionTest extends WorkspaceResolveServiceTestSupport {
    @Test
    void selectsGlobalExternalVersionsAcrossWorkspaceMembers() throws IOException {
        addArtifact("com.example", "other", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>other</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>lib</artifactId>
                      <version>2.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "lib", "2.0.0", pom("com.example", "lib", "2.0.0"));
        workspace("""
                [workspace]
                name = "bad"
                members = ["apps/api", "apps/worker"]

                [repositories]
                test = "%s"
                """.formatted(baseUri));
        member("apps/api", "api", """

                [dependencies]
                "com.example:app" = "1.0.0"
                """);
        member("apps/worker", "worker", """

                [dependencies]
                "com.example:other" = "1.0.0"
                """);

        ResolveResult result = service.resolve(tempDir, tempDir.resolve("cache"), false, false);

        assertEquals(3, result.resolvedCount());
        assertEquals(1, result.conflictCount());

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertFalse(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "lib"))
                        && lockPackage.version().equals("1.0.0")));
        LockPackage lib = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "lib")))
                .findFirst()
                .orElseThrow();
        assertEquals("2.0.0", lib.version());
        assertEquals(List.of("apps/api", "apps/worker"), lib.members());

        LockPackage app = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "app")))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("com.example:lib:2.0.0"), app.dependencies());
        assertTrue(lockfile.conflicts().stream().anyMatch(conflict ->
                conflict.packageId().equals(new PackageId("com.example", "lib"))
                        && conflict.selectedVersion().equals("2.0.0")
                        && conflict.requestedVersions().equals(List.of("1.0.0", "2.0.0"))));
    }

    @Test
    void directWorkspaceMemberDependencyWinsOverTransitiveWorkspaceRequest() throws IOException {
        addArtifact("com.example", "other", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>other</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>lib</artifactId>
                      <version>2.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "lib", "2.0.0", pom("com.example", "lib", "2.0.0"));
        workspace("""
                [workspace]
                name = "direct-wins"
                members = ["apps/api", "apps/worker"]

                [repositories]
                test = "%s"
                """.formatted(baseUri));
        member("apps/api", "api", """

                [dependencies]
                "com.example:lib" = "1.0.0"
                """);
        member("apps/worker", "worker", """

                [dependencies]
                "com.example:other" = "1.0.0"
                """);

        ResolveResult result = service.resolve(tempDir, tempDir.resolve("cache"), false, false);

        assertEquals(2, result.resolvedCount());
        assertEquals(1, result.conflictCount());

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage lib = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "lib")))
                .findFirst()
                .orElseThrow();
        assertEquals("1.0.0", lib.version());
        assertEquals(List.of("apps/api", "apps/worker"), lib.members());
        assertTrue(lib.direct());

        LockPackage other = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "other")))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("com.example:lib:1.0.0"), other.dependencies());
    }
}
