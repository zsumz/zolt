package sh.zolt.project.toolchain;

import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public record JavaToolchainRequest(
        String version,
        Optional<JavaDistribution> distribution,
        Set<JavaFeature> features,
        ToolchainPolicy policy) {
    public JavaToolchainRequest {
        version = cleanVersion(version);
        distribution = distribution == null ? Optional.empty() : distribution;
        features = orderedFeatures(features);
        policy = policy == null ? ToolchainPolicy.PREFER_MANAGED : policy;
    }

    public JavaToolchainRequest(
            String version,
            JavaDistribution distribution,
            Set<JavaFeature> features,
            ToolchainPolicy policy) {
        this(version, Optional.ofNullable(distribution), features, policy);
    }

    public static JavaToolchainRequest projectDefault(String projectJava) {
        return new JavaToolchainRequest(
                projectJava,
                Optional.empty(),
                Set.of(),
                ToolchainPolicy.ALLOW_SYSTEM);
    }

    public boolean requiresNativeImage() {
        return features.contains(JavaFeature.NATIVE_IMAGE);
    }

    public String distributionLabel() {
        return distribution.map(JavaDistribution::id).orElse("any");
    }

    public String featuresLabel() {
        if (features.isEmpty()) {
            return "none";
        }
        return String.join(", ", features.stream()
                .sorted(Comparator.comparing(JavaFeature::id))
                .map(JavaFeature::id)
                .toList());
    }

    private static String cleanVersion(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Java toolchain version is required.");
        }
        String normalized = value.strip();
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Java toolchain version must not contain control characters.");
        }
        return normalized;
    }

    private static Set<JavaFeature> orderedFeatures(Set<JavaFeature> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<JavaFeature> ordered = values.stream()
                .sorted(Comparator.comparing(JavaFeature::id))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        return Collections.unmodifiableSet(ordered);
    }
}
