package com.zolt.build;

import com.zolt.resolve.DependencyScope;
import java.util.List;

public record PackagePlanDependency(
        String coordinate,
        String version,
        DependencyScope scope,
        String disposition,
        String ruleName,
        String location,
        String reason,
        List<String> policies) {
    public PackagePlanDependency {
        coordinate = requireNonBlank(coordinate, "Package plan dependency coordinate is required.");
        version = requireNonBlank(version, "Package plan dependency version is required.");
        if (scope == null) {
            throw new PackageException("Package plan dependency scope is required.");
        }
        disposition = requireNonBlank(disposition, "Package plan dependency disposition is required.");
        ruleName = requireNonBlank(ruleName, "Package plan dependency rule name is required.");
        location = location == null ? "" : location;
        reason = requireNonBlank(reason, "Package plan dependency reason is required.");
        policies = policies == null ? List.of() : List.copyOf(policies);
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PackageException(message);
        }
        return value;
    }
}
