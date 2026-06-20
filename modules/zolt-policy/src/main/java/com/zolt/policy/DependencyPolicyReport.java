package com.zolt.policy;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record DependencyPolicyReport(
        Path projectRoot,
        List<PlatformPolicyDiagnostic> platforms,
        List<ConstraintPolicyDiagnostic> constraints,
        List<ExclusionPolicyDiagnostic> exclusions,
        List<DirectVersionDiagnostic> directVersions) {
    public DependencyPolicyReport {
        platforms = platforms == null ? List.of() : List.copyOf(platforms);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
        directVersions = directVersions == null ? List.of() : List.copyOf(directVersions);
    }

    public record PlatformPolicyDiagnostic(
            String platform,
            Optional<String> versionRef,
            List<ManagedPackageDiagnostic> packages) {
        public PlatformPolicyDiagnostic {
            versionRef = versionRef == null ? Optional.empty() : versionRef;
            packages = packages == null ? List.of() : List.copyOf(packages);
        }
    }

    public record ManagedPackageDiagnostic(
            String coordinate,
            String version,
            String scope,
            String policy) {}

    public record ConstraintPolicyDiagnostic(
            String coordinate,
            String kind,
            String requestedVersion,
            Optional<String> versionRef,
            Optional<String> selectedVersion,
            String status,
            Optional<String> source,
            Optional<String> reason,
            List<String> policies) {
        public ConstraintPolicyDiagnostic {
            versionRef = versionRef == null ? Optional.empty() : versionRef;
            selectedVersion = selectedVersion == null ? Optional.empty() : selectedVersion;
            source = source == null ? Optional.empty() : source;
            reason = reason == null ? Optional.empty() : reason;
            policies = policies == null ? List.of() : List.copyOf(policies);
        }
    }

    public record ExclusionPolicyDiagnostic(
            String coordinate,
            String status,
            Optional<String> reason,
            List<String> sources,
            List<String> policies) {
        public ExclusionPolicyDiagnostic {
            reason = reason == null ? Optional.empty() : reason;
            sources = sources == null ? List.of() : List.copyOf(sources);
            policies = policies == null ? List.of() : List.copyOf(policies);
        }
    }

    public record DirectVersionDiagnostic(
            String section,
            String coordinate,
            String version,
            Optional<String> versionRef,
            String status) {
        public DirectVersionDiagnostic {
            versionRef = versionRef == null ? Optional.empty() : versionRef;
        }
    }
}
