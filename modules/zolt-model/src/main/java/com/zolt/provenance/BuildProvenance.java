package com.zolt.provenance;

import java.time.Instant;
import java.util.Optional;

/**
 * The full build-provenance data set: git state plus the build timestamp, toolchain (Zolt + JDK), and
 * the resolution fingerprint. Assembled by {@link BuildProvenanceReader} from injected inputs so that
 * {@code zolt-model} stays the leaf lib and this stays unit-testable.
 *
 * <p>The {@code resolutionFingerprint} is best-effort — the caller passes it in from
 * {@code ZoltLockfile.projectResolutionFingerprint()} and omits it when there is no lockfile.
 */
public record BuildProvenance(
        GitProvenance git,
        Instant buildTimestamp,
        String zoltVersion,
        String jdkVersion,
        String jdkVendor,
        Optional<String> resolutionFingerprint) {

    public BuildProvenance {
        resolutionFingerprint = resolutionFingerprint == null ? Optional.empty() : resolutionFingerprint;
    }
}
