package com.zolt.resolve;

import java.util.Optional;

public record DependencyPolicyEffect(
        String kind,
        PackageId packageId,
        Optional<String> requestedVersion,
        Optional<String> source,
        String policy) {
    public DependencyPolicyEffect {
        requestedVersion = requestedVersion == null ? Optional.empty() : requestedVersion;
        source = source == null ? Optional.empty() : source;
    }
}
