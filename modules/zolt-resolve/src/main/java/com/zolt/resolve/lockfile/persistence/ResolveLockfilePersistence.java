package com.zolt.resolve.lockfile.persistence;

import com.zolt.dependency.DependencyScope;
import com.zolt.lockfile.LockfileFreshnessSummary;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.lockfile.ZoltLockfileWriter;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveOptions;
import com.zolt.resolve.metrics.ResolveMetrics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ResolveLockfilePersistence {
    private final ZoltLockfileReader lockfileReader = new ZoltLockfileReader();
    private final ZoltLockfileWriter lockfileWriter;

    public ResolveLockfilePersistence(ZoltLockfileWriter lockfileWriter) {
        this.lockfileWriter = lockfileWriter == null ? new ZoltLockfileWriter() : lockfileWriter;
    }

    public Path lockfilePath(Path projectDirectory) {
        return projectDirectory.resolve("zolt.lock");
    }

    public ResolveOptions prepare(Path lockfilePath, boolean locked, ResolveOptions options) {
        if (locked && !Files.isRegularFile(lockfilePath)) {
            throw new ResolveException(
                    "Locked resolve requires zolt.lock at "
                            + lockfilePath
                            + ". Run `zolt resolve` to create it, then retry `zolt resolve --locked`.");
        }
        if (locked && options.rejectLocalOverlays()) {
            rejectExistingLocalOverlayLockfile(lockfilePath);
        }
        if (!options.includeCoverageTooling() && existingLockfileHasCoverageTooling(lockfilePath)) {
            return options.withCoverageTooling();
        }
        return options;
    }

    public ResolveMetrics persist(
            Path lockfilePath,
            ZoltLockfile lockfile,
            ResolveMetrics metrics,
            boolean locked,
            ResolveOptions options) {
        if (options.rejectLocalOverlays()) {
            rejectLocalOverlayLockfile(lockfile);
        }
        if (locked) {
            long started = System.nanoTime();
            verifyLocked(lockfilePath, lockfile);
            return metrics.withLockfileVerificationNanos(elapsedSince(started));
        }
        long started = System.nanoTime();
        lockfileWriter.write(lockfilePath, lockfile);
        return metrics.withLockfileWriteNanos(elapsedSince(started));
    }

    private void rejectExistingLocalOverlayLockfile(Path lockfilePath) {
        String existing;
        try {
            existing = Files.readString(lockfilePath);
        } catch (IOException exception) {
            throw new ResolveException(
                    "Could not read zolt.lock at "
                            + lockfilePath
                            + " while checking local overlay origins. Check that the file exists and is readable.",
                    exception);
        }
        if (existing.contains("source = \"local-overlay:")) {
            throw new ResolveException(localOverlayRejectedMessage());
        }
    }

    private void rejectLocalOverlayLockfile(ZoltLockfile lockfile) {
        boolean hasLocalOverlay = lockfile.packages().stream()
                .anyMatch(lockPackage -> localOverlaySource(lockPackage.source()));
        if (hasLocalOverlay) {
            throw new ResolveException(localOverlayRejectedMessage());
        }
    }

    private boolean existingLockfileHasCoverageTooling(Path lockfilePath) {
        if (!Files.isRegularFile(lockfilePath)) {
            return false;
        }
        try {
            return lockfileReader.read(lockfilePath).packages().stream()
                    .anyMatch(lockPackage -> lockPackage.scope() == DependencyScope.TOOL_COVERAGE);
        } catch (LockfileReadException exception) {
            return false;
        }
    }

    private static boolean localOverlaySource(String source) {
        return source != null && source.startsWith("local-overlay:");
    }

    private static String localOverlayRejectedMessage() {
        return "Local repository overlay artifacts are not allowed for this resolve. "
                + "Run `zolt resolve` without local overlays to refresh zolt.lock from configured repositories, "
                + "or remove --no-local-overlays for a local development-only resolve.";
    }

    private void verifyLocked(Path lockfilePath, ZoltLockfile candidate) {
        String existing;
        try {
            existing = Files.readString(lockfilePath);
        } catch (IOException exception) {
            throw new ResolveException(
                    "Could not read zolt.lock at "
                            + lockfilePath
                            + " for locked resolve. Check that the file exists and is readable.",
                    exception);
        }

        String expected = lockfileWriter.write(candidate);
        if (!existing.equals(expected)) {
            String changedInputs = changedInputs(existing, candidate);
            throw new ResolveException(
                    "zolt.lock is out of date."
                            + changedInputs
                            + " Run `zolt resolve` to refresh it, then retry `zolt resolve --locked`.");
        }
    }

    private String changedInputs(String existing, ZoltLockfile candidate) {
        try {
            return LockfileFreshnessSummary.changedInputs(lockfileReader.read(existing), candidate);
        } catch (LockfileReadException exception) {
            return "";
        }
    }

    private static long elapsedSince(long started) {
        return Math.max(0L, System.nanoTime() - started);
    }
}
