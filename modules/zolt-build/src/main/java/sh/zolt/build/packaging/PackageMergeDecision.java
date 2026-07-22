package sh.zolt.build.packaging;

import sh.zolt.build.PackageException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record PackageMergeDecision(
        String kind,
        String path,
        Optional<String> target,
        List<String> sources) {
    private static final Set<String> SUPPORTED_KINDS = Set.of(
            "service-descriptor",
            "netty-version-metadata",
            "relocated-metadata",
            "omitted-module-descriptor",
            "overridden-duplicate");

    public PackageMergeDecision {
        if (kind == null || kind.isBlank()) {
            throw new PackageException("Package merge decision kind is required.");
        }
        if (!SUPPORTED_KINDS.contains(kind)) {
            throw new PackageException(
                    "Unsupported package merge decision kind `"
                            + kind
                            + "`. Regenerate package evidence with `zolt package`.");
        }
        if (path == null || path.isBlank()) {
            throw new PackageException("Package merge decision path is required.");
        }
        target = target == null ? Optional.empty() : target;
        if ("relocated-metadata".equals(kind) && target.filter(value -> !value.isBlank()).isEmpty()) {
            throw new PackageException("Relocated package merge decisions require a nonblank target path.");
        }
        if (!"relocated-metadata".equals(kind) && target.isPresent()) {
            throw new PackageException(
                    "Package merge decision kind `" + kind + "` must not declare a target path.");
        }
        sources = sources == null ? List.of() : List.copyOf(sources);
        if (sources.isEmpty()) {
            throw new PackageException("Package merge decision sources are required.");
        }
        if (sources.stream().anyMatch(source -> source == null || source.isBlank())) {
            throw new PackageException("Package merge decision sources must not be blank.");
        }
    }
}
