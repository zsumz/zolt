package com.zolt.quality;

import static com.zolt.quality.QualityCheckService.MANIFEST_METADATA;
import static com.zolt.quality.QualityCheckService.PACKAGE_CONTENTS;
import static com.zolt.quality.QualityCheckService.PACKAGE_METADATA;

import com.zolt.build.PackageEvidenceManifest;
import com.zolt.build.PackageEvidenceManifestReader;
import com.zolt.build.PackageEvidenceManifestWriter;
import com.zolt.build.PackageException;
import com.zolt.build.PackagePlan;
import com.zolt.build.PackagePlanDependency;
import com.zolt.build.PackagePlanService;
import com.zolt.build.PackagePlanWarning;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.project.BuildSettings;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.PublicationMetadata;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

final class PackageQualityCheck {
    private static final Set<String> ZOLT_OWNED_MANIFEST_ATTRIBUTES = Set.of(
            "manifest-version",
            "main-class");

    private final PackagePlanService packagePlanService;
    private final PackageEvidenceManifestReader packageEvidenceManifestReader;

    PackageQualityCheck(
            PackagePlanService packagePlanService,
            PackageEvidenceManifestReader packageEvidenceManifestReader) {
        this.packagePlanService = packagePlanService;
        this.packageEvidenceManifestReader = packageEvidenceManifestReader;
    }

    QualityCheckResult checkMetadata(
            Optional<String> member,
            Path projectRoot,
            ProjectConfig config) {
        PackageSettings settings = config.packageSettings();
        if (!usesLibraryPackageProfile(settings)) {
            return QualityCheckResult.passed(
                    PACKAGE_METADATA,
                    member,
                    config.project().name(),
                    "No library package metadata is requested.");
        }

        if (!settings.sources()) {
            return QualityCheckResult.failed(
                    PACKAGE_METADATA,
                    member,
                    "[package].sources",
                    "Library package metadata is enabled, but sources jar generation is disabled.",
                    "Set [package].sources = true for library projects.");
        }
        if (hasSourceFiles(projectRoot, List.of(config.build().source())) && !settings.javadoc()) {
            return QualityCheckResult.failed(
                    PACKAGE_METADATA,
                    member,
                    "[package].javadoc",
                    "Library package metadata is enabled, but javadoc jar generation is disabled.",
                    "Set [package].javadoc = true when publishing Java APIs.");
        }
        if (hasSourceFiles(projectRoot, testSourceRoots(config.build())) && !settings.tests()) {
            return QualityCheckResult.failed(
                    PACKAGE_METADATA,
                    member,
                    "[package].tests",
                    "Test sources are present, but tests jar generation is disabled for this library package.",
                    "Set [package].tests = true or remove test sources from the library artifact story.");
        }

        Optional<QualityCheckResult> missingMetadata = firstMissingPublicationMetadata(member, settings.metadata());
        if (missingMetadata.isPresent()) {
            return missingMetadata.orElseThrow();
        }

        return QualityCheckResult.passed(
                PACKAGE_METADATA,
                member,
                config.project().name(),
                "Library package metadata is complete.");
    }

    List<QualityCheckResult> checkContents(
            Optional<String> member,
            Path projectRoot,
            ProjectConfig config,
            Path lockfilePath,
            boolean requirePackage) {
        if (!Files.isRegularFile(lockfilePath)) {
            return List.of(QualityCheckResult.failed(
                    PACKAGE_CONTENTS,
                    member,
                    "zolt.lock",
                    "Package content diagnostics require zolt.lock.",
                    member.isPresent() ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`."));
        }
        PackagePlan plan;
        try {
            plan = packagePlanService.plan(projectRoot, config, lockfilePath);
        } catch (LockfileReadException exception) {
            return List.of(QualityCheckResult.failed(
                    PACKAGE_CONTENTS,
                    member,
                    "zolt.lock",
                    exception.getMessage(),
                    member.isPresent() ? "Run `zolt resolve --workspace`." : "Run `zolt resolve`."));
        }
        if (plan.warnings().isEmpty()) {
            if (requirePackage && !Files.isRegularFile(plan.archivePath())) {
                return List.of(QualityCheckResult.failed(
                        PACKAGE_CONTENTS,
                        member,
                        QualityCheckText.displayPath(projectRoot, plan.archivePath()),
                        "CI context requires the configured package artifact, but it is missing.",
                        "Run `zolt package` before `zolt check --context ci --require-package`."));
            }
            Optional<QualityCheckResult> staleEvidence = stalePackageEvidence(member, plan);
            if (staleEvidence.isPresent()) {
                return List.of(staleEvidence.orElseThrow());
            }
            List<QualityCheckResult> results = new ArrayList<>();
            long policyEffects = plan.dependencies().stream()
                    .filter(dependency -> !dependency.policies().isEmpty())
                    .count();
            String policyMessage = policyEffects == 0
                    ? ""
                    : " " + policyEffects + " dependencies include dependency policy effects.";
            results.add(QualityCheckResult.passed(
                    PACKAGE_CONTENTS,
                    member,
                    config.project().name(),
                    "Package mode `"
                            + plan.mode().configValue()
                            + "` has "
                            + plan.dependencies().size()
                            + " dependency dispositions."
                            + policyMessage));
            results.addAll(packageContentRuleDiagnostics(member, plan));
            return List.copyOf(results);
        }
        List<QualityCheckResult> results = new ArrayList<>();
        for (PackagePlanWarning warning : plan.warnings()) {
            results.add(QualityCheckResult.failed(
                    PACKAGE_CONTENTS,
                    member,
                    warning.subject(),
                    warning.message(),
                    warning.nextStep()));
        }
        return List.copyOf(results);
    }

    private static List<QualityCheckResult> packageContentRuleDiagnostics(
            Optional<String> member,
            PackagePlan plan) {
        Map<PackageContentRuleKey, PackageContentRuleStats> stats = new TreeMap<>();
        for (PackagePlanDependency dependency : plan.dependencies()) {
            PackageContentRuleKey key = new PackageContentRuleKey(
                    dependency.ruleName(),
                    dependency.scope().lockfileName(),
                    dependency.disposition(),
                    locationShape(dependency.location()));
            stats.computeIfAbsent(key, ignored -> new PackageContentRuleStats()).add(dependency);
        }
        List<QualityCheckResult> results = new ArrayList<>();
        for (Map.Entry<PackageContentRuleKey, PackageContentRuleStats> entry : stats.entrySet()) {
            PackageContentRuleKey key = entry.getKey();
            PackageContentRuleStats value = entry.getValue();
            String policyMessage = value.policyEffects() == 0
                    ? ""
                    : " "
                            + value.policyEffects()
                            + " "
                            + QualityCheckText.verb(value.policyEffects(), "includes", "include")
                            + " dependency policy effects.";
            results.add(QualityCheckResult.passed(
                    PACKAGE_CONTENTS,
                    member,
                    "rule:" + key.ruleName(),
                    value.count()
                            + " "
                            + QualityCheckText.plural(value.count(), "dependency", "dependencies")
                            + " "
                            + QualityCheckText.verb(value.count(), "uses", "use")
                            + " package rule `"
                            + key.ruleName()
                            + "` with scope `"
                            + key.scope()
                            + "`, disposition `"
                            + key.disposition()
                            + "`, and location `"
                            + key.location()
                            + "`."
                            + policyMessage));
        }
        return List.copyOf(results);
    }

    private static String locationShape(String location) {
        if (location == null || location.isBlank()) {
            return "none";
        }
        int slash = location.lastIndexOf('/');
        if (slash <= 0 || location.endsWith("/")) {
            return location;
        }
        return location.substring(0, slash + 1) + "*";
    }


    private record PackageContentRuleKey(
            String ruleName,
            String scope,
            String disposition,
            String location) implements Comparable<PackageContentRuleKey> {
        @Override
        public int compareTo(PackageContentRuleKey other) {
            int rule = ruleName.compareTo(other.ruleName);
            if (rule != 0) {
                return rule;
            }
            int scopeComparison = scope.compareTo(other.scope);
            if (scopeComparison != 0) {
                return scopeComparison;
            }
            int dispositionComparison = disposition.compareTo(other.disposition);
            if (dispositionComparison != 0) {
                return dispositionComparison;
            }
            return location.compareTo(other.location);
        }
    }

    private static final class PackageContentRuleStats {
        private long count;
        private long policyEffects;

        void add(PackagePlanDependency dependency) {
            count++;
            if (!dependency.policies().isEmpty()) {
                policyEffects++;
            }
        }

        long count() {
            return count;
        }

        long policyEffects() {
            return policyEffects;
        }
    }

    private Optional<QualityCheckResult> stalePackageEvidence(Optional<String> member, PackagePlan plan) {
        Path archive = plan.archivePath();
        if (!Files.exists(archive)) {
            return Optional.empty();
        }
        Path root = plan.projectRoot().toAbsolutePath().normalize();
        Path manifestPath = PackageEvidenceManifestWriter.evidenceManifestPath(archive);
        if (!Files.isRegularFile(manifestPath)) {
            return Optional.of(QualityCheckResult.failed(
                    PACKAGE_CONTENTS,
                    member,
                    QualityCheckText.displayPath(root, archive),
                    "Package artifact exists, but package evidence manifest is missing.",
                    "Run `zolt package` to regenerate " + QualityCheckText.displayPath(root, manifestPath) + "."));
        }
        try {
            PackageEvidenceManifest manifest = packageEvidenceManifestReader.read(manifestPath);
            String actualSha256 = sha256(archive);
            if (!actualSha256.equals(manifest.archiveSha256())) {
                return Optional.of(QualityCheckResult.failed(
                        PACKAGE_CONTENTS,
                        member,
                        QualityCheckText.displayPath(root, manifestPath),
                        "Package evidence manifest is stale for `" + QualityCheckText.displayPath(root, archive) + "`.",
                        "Run `zolt package` to regenerate the artifact and evidence manifest."));
            }
        } catch (PackageException exception) {
            return Optional.of(QualityCheckResult.failed(
                    PACKAGE_CONTENTS,
                    member,
                    QualityCheckText.displayPath(root, manifestPath),
                    exception.getMessage(),
                    "Run `zolt package` to regenerate package evidence."));
        }
        return Optional.empty();
    }

    private static String sha256(Path path) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (java.io.IOException exception) {
            throw new PackageException(
                    "Could not read package artifact at "
                            + path
                            + ". Check that the file is readable and retry.",
                    exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new PackageException("Could not compute package artifact checksum because SHA-256 is unavailable.", exception);
        }
    }

    QualityCheckResult checkManifestMetadata(
            Optional<String> member,
            ProjectConfig config) {
        PackageSettings settings = config.packageSettings();
        for (String attributeName : settings.manifestAttributes().keySet()) {
            if (ZOLT_OWNED_MANIFEST_ATTRIBUTES.contains(attributeName.toLowerCase(Locale.ROOT))) {
                return QualityCheckResult.failed(
                        MANIFEST_METADATA,
                        member,
                        "[package.manifest]." + attributeName,
                        "Manifest attribute `" + attributeName + "` is owned by Zolt.",
                        "Remove it from [package.manifest]; use [project].main for Main-Class.");
            }
        }

        if (!usesLibraryPackageProfile(settings)) {
            return QualityCheckResult.passed(
                    MANIFEST_METADATA,
                    member,
                config.project().name(),
                "No library manifest metadata is requested.");
        }

        if (!containsManifestAttribute(settings, "Automatic-Module-Name")) {
            return QualityCheckResult.failed(
                    MANIFEST_METADATA,
                    member,
                    "[package.manifest].Automatic-Module-Name",
                    "Library package metadata is enabled, but Automatic-Module-Name is missing.",
                    "Add [package.manifest].\"Automatic-Module-Name\" with a stable Java module name.");
        }

        return QualityCheckResult.passed(
                MANIFEST_METADATA,
                member,
                config.project().name(),
                "Library manifest metadata is deterministic.");
    }

    private static boolean containsManifestAttribute(PackageSettings settings, String name) {
        return settings.manifestAttributes().keySet().stream()
                .anyMatch(candidate -> candidate.equalsIgnoreCase(name));
    }

    private static boolean usesLibraryPackageProfile(PackageSettings settings) {
        return settings.sources()
                || settings.javadoc()
                || settings.tests()
                || hasPublicationMetadata(settings.metadata())
                || !settings.manifestAttributes().isEmpty();
    }

    private static boolean hasPublicationMetadata(PublicationMetadata metadata) {
        return !metadata.name().isBlank()
                || !metadata.description().isBlank()
                || !metadata.url().isBlank()
                || !metadata.license().isBlank()
                || !metadata.developers().isEmpty()
                || !metadata.scm().isBlank()
                || !metadata.issues().isBlank();
    }

    private static Optional<QualityCheckResult> firstMissingPublicationMetadata(
            Optional<String> member,
            PublicationMetadata metadata) {
        if (metadata.name().isBlank()) {
            return missingPublicationField(member, "name");
        }
        if (metadata.description().isBlank()) {
            return missingPublicationField(member, "description");
        }
        if (metadata.url().isBlank()) {
            return missingPublicationField(member, "url");
        }
        if (metadata.license().isBlank()) {
            return missingPublicationField(member, "license");
        }
        if (metadata.developers().isEmpty()) {
            return missingPublicationField(member, "developers");
        }
        if (metadata.scm().isBlank()) {
            return missingPublicationField(member, "scm");
        }
        if (metadata.issues().isBlank()) {
            return missingPublicationField(member, "issues");
        }
        return Optional.empty();
    }

    private static Optional<QualityCheckResult> missingPublicationField(Optional<String> member, String field) {
        return Optional.of(QualityCheckResult.failed(
                PACKAGE_METADATA,
                member,
                "[package.metadata]." + field,
                "Library package metadata is enabled, but publication metadata field `" + field + "` is missing.",
                "Fill [package.metadata]." + field + " in zolt.toml."));
    }

    private static List<String> testSourceRoots(BuildSettings build) {
        List<String> roots = new ArrayList<>();
        roots.add(build.test());
        roots.addAll(build.testSources());
        roots.addAll(build.groovyTestSources());
        return List.copyOf(new LinkedHashSet<>(roots));
    }

    private static boolean hasSourceFiles(Path projectRoot, List<String> roots) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        for (String root : roots) {
            if (root == null || root.isBlank()) {
                continue;
            }
            Path sourceRoot = normalizedRoot.resolve(root).normalize();
            if (!sourceRoot.startsWith(normalizedRoot) || !Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (var stream = Files.find(sourceRoot, Integer.MAX_VALUE, (path, attributes) ->
                    attributes.isRegularFile() && sourceLike(path))) {
                if (stream.findFirst().isPresent()) {
                    return true;
                }
            } catch (java.io.IOException exception) {
                return true;
            }
        }
        return false;
    }

    private static boolean sourceLike(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".java") || fileName.endsWith(".groovy");
    }
}
