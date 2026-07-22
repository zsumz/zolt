package sh.zolt.explain.verify;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.lockfile.LockPackage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Maps a Zolt member's resolved {@link LockPackage} set (from
 * {@code ResolveService.resolveLockfile(...).lockfile().packages()}) into a {@link ResolvedModule}
 * for comparison against the matching Maven reactor module.
 *
 * <p>Each {@link LockPackage} carries exactly one {@link DependencyScope}; a package needed on
 * several scopes appears as several entries, so bucketing by {@link VerifyScope#fromZoltScope} is
 * direct. Zolt scopes outside the four compared ones (dev, processor, test-processor,
 * quarkus-deployment, tool-*) are counted in {@link ResolvedModule#unmappedScopes()} instead of
 * being forced into the comparison, because Maven's {@code dependency:tree} does not emit annotation
 * processors or framework tooling as resolved dependencies.
 *
 * <p>Classifier: the Zolt lockfile does not persist an artifact classifier as a discrete field, so it
 * is recovered best-effort from the cached artifact file name ({@code artifact-version-classifier.ext}).
 * This keeps the comparison identity ({@code group:artifact[:classifier]}) symmetric with the Maven
 * side. When no artifact path is present (e.g. a workspace/pom-only entry) the classifier is empty.
 */
public final class ZoltModuleMapper {

    public ResolvedModule fromLockPackages(
            String groupId,
            String artifactId,
            String version,
            List<LockPackage> packages) {
        Map<VerifyScope, List<ResolvedArtifact>> scopes = new TreeMap<>();
        Map<String, Integer> unmapped = new TreeMap<>();
        if (packages != null) {
            for (LockPackage pkg : packages) {
                map(pkg, scopes, unmapped);
            }
        }
        return new ResolvedModule(groupId, artifactId, version, "jar", scopes, unmapped);
    }

    private void map(
            LockPackage pkg,
            Map<VerifyScope, List<ResolvedArtifact>> scopes,
            Map<String, Integer> unmapped) {
        Optional<VerifyScope> scope = VerifyScope.fromZoltScope(pkg.scope());
        if (scope.isEmpty()) {
            unmapped.merge(pkg.scope().lockfileName(), 1, Integer::sum);
            return;
        }
        String type = pkg.artifactType().filter(value -> !value.isBlank()).orElse("jar");
        String classifier = deriveClassifier(
                pkg.packageId().artifactId(), pkg.version(), pkg.jar().or(pkg::artifact));
        ResolvedArtifact artifact = new ResolvedArtifact(
                pkg.packageId().groupId(),
                pkg.packageId().artifactId(),
                type,
                classifier,
                pkg.version());
        scopes.computeIfAbsent(scope.get(), key -> new ArrayList<>()).add(artifact);
    }

    /**
     * Extracts a classifier from a cached artifact path whose file name is
     * {@code artifactId-version[-classifier].ext}. Returns empty when the file name is the plain
     * {@code artifactId-version.ext} form or cannot be interpreted.
     */
    static String deriveClassifier(String artifactId, String version, Optional<String> artifactPath) {
        if (artifactPath.isEmpty()) {
            return "";
        }
        String path = artifactPath.get().replace('\\', '/');
        int slash = path.lastIndexOf('/');
        String fileName = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = fileName.lastIndexOf('.');
        String stem = dot >= 0 ? fileName.substring(0, dot) : fileName;
        String prefix = artifactId + "-" + version;
        if (!stem.startsWith(prefix)) {
            return "";
        }
        String remainder = stem.substring(prefix.length());
        if (remainder.isEmpty()) {
            return "";
        }
        if (remainder.charAt(0) == '-') {
            return remainder.substring(1);
        }
        return "";
    }
}
