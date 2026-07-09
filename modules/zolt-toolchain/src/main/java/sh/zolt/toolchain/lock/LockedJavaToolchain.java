package sh.zolt.toolchain.lock;

import sh.zolt.project.toolchain.JavaDistribution;
import sh.zolt.project.toolchain.JavaFeature;
import sh.zolt.project.toolchain.JavaToolchainRequest;
import sh.zolt.toolchain.platform.HostPlatform;
import java.util.Comparator;
import java.util.Set;

public record LockedJavaToolchain(
        String id,
        JavaToolchainRequest request,
        HostPlatform platform,
        String resolvedVersion,
        JavaDistribution resolvedDistribution,
        String catalog,
        String artifactUri,
        String artifactSha256,
        JavaToolchainLayout layout) {
    public LockedJavaToolchain {
        id = clean(id, "Java toolchain lock id is required.");
        if (request == null) {
            throw new IllegalArgumentException("Java toolchain request is required.");
        }
        if (platform == null) {
            throw new IllegalArgumentException("Java toolchain platform is required.");
        }
        resolvedVersion = clean(resolvedVersion, "Java toolchain resolved version is required.");
        if (resolvedDistribution == null) {
            throw new IllegalArgumentException("Java toolchain resolved distribution is required.");
        }
        catalog = clean(catalog, "Java toolchain catalog reference is required.");
        artifactUri = cleanOptional(artifactUri);
        artifactSha256 = cleanOptional(artifactSha256);
        layout = layout == null ? JavaToolchainLayout.standard(request.requiresNativeImage()) : layout;
    }

    public LockedJavaToolchain(
            String id,
            JavaToolchainRequest request,
            HostPlatform platform,
            String resolvedVersion,
            JavaDistribution resolvedDistribution,
            String catalog,
            JavaToolchainLayout layout) {
        this(id, request, platform, resolvedVersion, resolvedDistribution, catalog, "", "", layout);
    }

    public String featureList() {
        if (request.features().isEmpty()) {
            return "";
        }
        return String.join(", ", request.features().stream()
                .sorted(Comparator.comparing(JavaFeature::id))
                .map(JavaFeature::id)
                .toList());
    }

    public Set<JavaFeature> features() {
        return request.features();
    }

    private static String clean(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }

    private static String cleanOptional(String value) {
        return value == null ? "" : value.strip();
    }
}
