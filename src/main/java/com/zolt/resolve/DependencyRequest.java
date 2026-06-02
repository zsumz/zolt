package com.zolt.resolve;

public record DependencyRequest(
        PackageId packageId,
        String requestedVersion,
        DependencyScope scope,
        RequestOrigin origin) {
    public boolean direct() {
        return origin == RequestOrigin.DIRECT;
    }
}
