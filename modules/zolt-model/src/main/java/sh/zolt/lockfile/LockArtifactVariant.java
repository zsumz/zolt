package sh.zolt.lockfile;

import sh.zolt.dependency.PackageId;
import java.util.Optional;

/**
 * The artifact-variant discriminator of a lock entry beyond its {@link PackageId}: the Maven
 * type/extension and the optional classifier. Two lock entries that share a {@code (groupId,
 * artifactId)} but differ in variant — a plain {@code jar} versus a {@code linux-x86_64} classified
 * jar, or a {@code jar} versus a {@code .zip} — are DISTINCT Maven artifacts carrying distinct bytes;
 * they must coexist as separate entries rather than collapse onto one (which silently routes the wrong
 * bytes onto a member's classpath).
 *
 * <p>Neither dimension is stored structurally on {@link LockPackage}. {@code extension} is the Maven
 * {@code <type>}, recovered from {@link LockPackage#artifactType()} (the resolver stores the type
 * verbatim and uses it as the on-disk extension), defaulting to {@code jar}. {@code classifier} is
 * recovered from the artifact filename by stripping the known {@code <artifactId>-<version>-} prefix
 * and {@code .<ext>} suffix. This deliberately mirrors the derivation the sbom already trusts in
 * production ({@code sh.zolt.sbom.LockArtifacts}), so a lock entry's variant identity is consistent
 * everywhere it is computed and no additional lock field — nor codec round-trip — is required.
 */
public record LockArtifactVariant(String extension, Optional<String> classifier)
        implements Comparable<LockArtifactVariant> {
    public LockArtifactVariant {
        extension = extension == null || extension.isBlank() ? "jar" : extension;
        classifier = classifier == null ? Optional.empty() : classifier;
    }

    /** Derives the variant identity of a lock entry from its artifact fields. */
    public static LockArtifactVariant of(LockPackage lockPackage) {
        return new LockArtifactVariant(extension(lockPackage), classifier(lockPackage));
    }

    /**
     * A stable, filename-free key that orders and disambiguates the variants of one {@link PackageId}.
     * The {@code |} delimiter cannot appear in a Maven extension or classifier, so the key is unambiguous
     * even when concatenated into a larger colon-delimited map key. A plain {@code jar} with no classifier
     * yields exactly {@code "jar"} — the overwhelming common case — so appending this key as a sort
     * tiebreak (engaged only when two entries already share their primary key) leaves every lock without
     * variants byte-identical.
     */
    public String key() {
        return classifier.map(value -> extension + "|" + value).orElse(extension);
    }

    @Override
    public int compareTo(LockArtifactVariant other) {
        int byExtension = extension.compareTo(other.extension);
        if (byExtension != 0) {
            return byExtension;
        }
        return classifier.orElse("").compareTo(other.classifier.orElse(""));
    }

    private static String extension(LockPackage lockPackage) {
        if (lockPackage.artifactType().isPresent()) {
            return lockPackage.artifactType().orElseThrow();
        }
        return fileName(lockPackage).map(LockArtifactVariant::extensionOf).orElse("jar");
    }

    private static Optional<String> classifier(LockPackage lockPackage) {
        Optional<String> fileName = fileName(lockPackage);
        if (fileName.isEmpty()) {
            return Optional.empty();
        }
        String prefix = lockPackage.packageId().artifactId() + "-" + lockPackage.version();
        String name = fileName.orElseThrow();
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        if (base.length() > prefix.length() + 1 && base.startsWith(prefix) && base.charAt(prefix.length()) == '-') {
            return Optional.of(base.substring(prefix.length() + 1));
        }
        return Optional.empty();
    }

    private static Optional<String> fileName(LockPackage lockPackage) {
        return lockPackage.artifact().or(lockPackage::jar).map(LockArtifactVariant::lastSegment);
    }

    private static String lastSegment(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1) : "jar";
    }
}
