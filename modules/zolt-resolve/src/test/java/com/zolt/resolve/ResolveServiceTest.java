package com.zolt.resolve;

import com.zolt.resolve.support.ResolveServiceTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.PackageId;
import com.zolt.lockfile.ZoltLockfile;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ResolveServiceTest extends ResolveServiceTestSupport {
    @Test
    void resolveDownloadsArtifactsAndWritesLockfile() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, config(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        assertEquals(4, result.downloadCount());
        assertEquals(0, result.conflictCount());
        assertEquals(projectDir.resolve("zolt.lock"), result.lockfilePath());
        assertTrue(result.metrics().pomDownloadNanos() > 0);
        assertTrue(result.metrics().artifactDownloadNanos() > 0);
        assertTrue(result.metrics().pomCacheHitNanos() > 0);
        assertEquals(0, result.metrics().artifactCacheHitNanos());
        assertTrue(result.metrics().rawPomParseNanos() > 0);
        assertTrue(result.metrics().effectivePomBuildNanos() > 0);
        assertTrue(result.metrics().graphTraversalNanos() > 0);
        assertTrue(result.metrics().lockfileAssemblyNanos() > 0);
        assertTrue(result.metrics().lockfileWriteNanos() > 0);
        assertEquals(0, result.metrics().lockfileVerificationNanos());
        assertTrue(Files.exists(result.lockfilePath()));

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.projectResolutionFingerprint().orElseThrow().startsWith("sha256:"));
        assertEquals(2, lockfile.packages().size());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app")) && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "lib")) && !lockPackage.direct()));
    }
}
