package com.zolt.lockfile;

import java.util.List;
import java.util.Optional;

public record ZoltLockfile(
        int version,
        Optional<String> aliasFingerprint,
        Optional<String> projectResolutionFingerprint,
        List<String> projectResolutionInputFingerprints,
        List<LockPackage> packages,
        List<LockConflict> conflicts,
        List<LockPolicyEffect> policyEffects) {
    public static final int CURRENT_VERSION = 1;

    public ZoltLockfile(
            int version,
            List<LockPackage> packages,
            List<LockConflict> conflicts) {
        this(version, Optional.empty(), Optional.empty(), List.of(), packages, conflicts, List.of());
    }

    public ZoltLockfile(
            int version,
            List<LockPackage> packages,
            List<LockConflict> conflicts,
            List<LockPolicyEffect> policyEffects) {
        this(version, Optional.empty(), Optional.empty(), List.of(), packages, conflicts, policyEffects);
    }

    public ZoltLockfile(
            int version,
            Optional<String> aliasFingerprint,
            List<LockPackage> packages,
            List<LockConflict> conflicts,
            List<LockPolicyEffect> policyEffects) {
        this(version, aliasFingerprint, Optional.empty(), List.of(), packages, conflicts, policyEffects);
    }

    public ZoltLockfile(
            int version,
            Optional<String> aliasFingerprint,
            Optional<String> projectResolutionFingerprint,
            List<LockPackage> packages,
            List<LockConflict> conflicts,
            List<LockPolicyEffect> policyEffects) {
        this(version, aliasFingerprint, projectResolutionFingerprint, List.of(), packages, conflicts, policyEffects);
    }

    public ZoltLockfile {
        aliasFingerprint = aliasFingerprint == null ? Optional.empty() : aliasFingerprint;
        projectResolutionFingerprint = projectResolutionFingerprint == null
                ? Optional.empty()
                : projectResolutionFingerprint;
        projectResolutionInputFingerprints = projectResolutionInputFingerprints == null
                ? List.of()
                : List.copyOf(projectResolutionInputFingerprints);
        packages = List.copyOf(packages);
        conflicts = List.copyOf(conflicts);
        policyEffects = policyEffects == null ? List.of() : List.copyOf(policyEffects);
    }
}
