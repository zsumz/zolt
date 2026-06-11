package com.zolt.lockfile;

import com.zolt.resolve.PackageId;
import java.util.Optional;

public record LockPolicyEffect(
        String kind,
        PackageId packageId,
        Optional<String> requestedVersion,
        Optional<String> source,
        String policy) {
    public LockPolicyEffect {
        requestedVersion = requestedVersion == null ? Optional.empty() : requestedVersion;
        source = source == null ? Optional.empty() : source;
    }
}
