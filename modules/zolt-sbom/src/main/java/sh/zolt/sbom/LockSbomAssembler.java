package sh.zolt.sbom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.ProjectConfig;

/**
 * Assembles an {@link SbomModel} from a resolved {@link ZoltLockfile} and its {@link ProjectConfig}.
 *
 * <p>This is a pure read of the lock — the dependency edges, hashes, and per-member attribution are
 * already persisted there (see the design record). No re-resolution and no network. Scope filtering,
 * multi-scope dedup (required wins), edge filtering to surviving endpoints, and deterministic sorting
 * all happen here so the writers only serialize.
 */
public final class LockSbomAssembler {
    private final SpdxLicenseMapping licenseMapping = new SpdxLicenseMapping();

    public SbomModel assemble(
            ProjectConfig config,
            ZoltLockfile lockfile,
            SbomScopeSelection selection,
            Optional<String> timestamp,
            String toolVersion) {
        return assemble(config, lockfile, selection, timestamp, toolVersion, LicenseIndex.empty());
    }

    public SbomModel assemble(
            ProjectConfig config,
            ZoltLockfile lockfile,
            SbomScopeSelection selection,
            Optional<String> timestamp,
            String toolVersion,
            LicenseIndex licenses) {
        SbomComponent root = rootComponent(config);

        // Dedup components by bom-ref (purl). Multi-scope duplicates merge; required wins.
        Map<String, ComponentAccumulator> byRef = new LinkedHashMap<>();
        Map<String, String> coordinateToRef = new TreeMap<>();
        List<LockPackage> included = new ArrayList<>();
        for (LockPackage lockPackage : lockfile.packages()) {
            SbomScopeGroup group = SbomScopeGroup.of(lockPackage.scope());
            if (!selection.includes(group)) {
                continue;
            }
            included.add(lockPackage);
            accumulate(byRef, coordinateToRef, lockPackage, group);
        }

        List<SbomComponent> components = byRef.values().stream()
                .map(accumulator -> accumulator.toComponent(
                        emittableLicenses(licenses.forCoordinate(accumulator.coordinate()))))
                .sorted(Comparator.comparing(SbomComponent::bomRef))
                .toList();

        List<SbomDependency> dependencies = dependencyGraph(root, included, byRef, coordinateToRef);
        String serialNumber = serialNumber(config, lockfile, components);
        return new SbomModel(
                serialNumber,
                timestamp,
                List.of(new SbomTool("zolt", toolVersion)),
                root,
                components,
                dependencies);
    }

    /** SBOM-emittable licenses: UNKNOWN dropped, deduplicated, sorted by label (id/name). */
    static List<SbomLicense> emittableLicenses(List<SbomLicense> licenses) {
        return licenses.stream()
                .filter(license -> license.status() != SbomLicenseStatus.UNKNOWN)
                .distinct()
                .sorted(Comparator.comparing(SbomLicense::label))
                .toList();
    }

    private void accumulate(
            Map<String, ComponentAccumulator> byRef,
            Map<String, String> coordinateToRef,
            LockPackage lockPackage,
            SbomScopeGroup group) {
        String groupId = lockPackage.packageId().groupId();
        String artifactId = lockPackage.packageId().artifactId();
        String version = lockPackage.version();
        String extension = extension(lockPackage);
        Optional<String> classifier = classifier(lockPackage);
        String purl = PurlWriter.purl(groupId, artifactId, version, extension, classifier);

        ComponentAccumulator accumulator = byRef.computeIfAbsent(
                purl,
                ref -> new ComponentAccumulator(
                        SbomComponentType.LIBRARY, ref, groupId, artifactId, version, ref));
        accumulator.raise(group.componentScope());
        hash(lockPackage).ifPresent(accumulator.hashes::add);

        // Lock edges reference the bare "g:a:v"; map it to the base (classifier-free) bom-ref.
        String coordinate = groupId + ":" + artifactId + ":" + version;
        if (classifier.isEmpty()) {
            coordinateToRef.put(coordinate, purl);
        } else {
            coordinateToRef.putIfAbsent(coordinate, purl);
        }
    }

    private List<SbomDependency> dependencyGraph(
            SbomComponent root,
            List<LockPackage> included,
            Map<String, ComponentAccumulator> byRef,
            Map<String, String> coordinateToRef) {
        Map<String, TreeSet<String>> edges = new TreeMap<>();
        edges.put(root.bomRef(), new TreeSet<>());
        for (ComponentAccumulator accumulator : byRef.values()) {
            edges.computeIfAbsent(accumulator.bomRef, ref -> new TreeSet<>());
        }

        for (LockPackage lockPackage : included) {
            String ref = purlFor(lockPackage);
            if (lockPackage.direct()) {
                edges.get(root.bomRef()).add(ref);
            }
            TreeSet<String> dependsOn = edges.get(ref);
            for (String edge : lockPackage.dependencies()) {
                String target = coordinateToRef.get(edge);
                if (target != null) {
                    dependsOn.add(target);
                }
            }
        }

        List<SbomDependency> dependencies = new ArrayList<>();
        for (var edge : edges.entrySet()) {
            dependencies.add(new SbomDependency(edge.getKey(), List.copyOf(edge.getValue())));
        }
        return dependencies;
    }

    private String serialNumber(ProjectConfig config, ZoltLockfile lockfile, List<SbomComponent> components) {
        String seed = lockfile.projectResolutionFingerprint()
                .filter(fingerprint -> !fingerprint.isBlank())
                .orElseGet(() -> SbomSerialNumber.fallbackSeed(
                        rootCoordinate(config),
                        components.stream().map(SbomComponent::purl).toList()));
        return SbomSerialNumber.serialNumber(seed);
    }

    private SbomComponent rootComponent(ProjectConfig config) {
        String group = config.project().group();
        String name = config.project().name();
        String version = config.project().version();
        String extension = rootExtension(config);
        String purl = PurlWriter.purl(group, name, version, extension, Optional.empty());
        return new SbomComponent(
                rootType(config),
                purl,
                group,
                name,
                version,
                purl,
                SbomComponentScope.REQUIRED,
                List.of(),
                emittableLicenses(rootLicenses(config)));
    }

    /** The root license is authoritative from config ([package.metadata]), not POM-extracted. */
    private List<SbomLicense> rootLicenses(ProjectConfig config) {
        String license = config.packageSettings().metadata().license();
        String licenseUrl = config.packageSettings().metadata().licenseUrl();
        Optional<String> name = license.isBlank() ? Optional.empty() : Optional.of(license);
        Optional<String> url = licenseUrl.isBlank() ? Optional.empty() : Optional.of(licenseUrl);
        if (name.isEmpty() && url.isEmpty()) {
            return List.of();
        }
        return List.of(licenseMapping.spdxId(name, url)
                .map(SbomLicense::spdx)
                .orElseGet(() -> SbomLicense.unmapped(name, url)));
    }

    private static SbomComponentType rootType(ProjectConfig config) {
        if (config.project().main().isPresent()) {
            return SbomComponentType.APPLICATION;
        }
        return switch (config.packageSettings().mode()) {
            case SPRING_BOOT, SPRING_BOOT_WAR, WAR, QUARKUS, UBER -> SbomComponentType.APPLICATION;
            case THIN -> SbomComponentType.LIBRARY;
        };
    }

    private static String rootExtension(ProjectConfig config) {
        return switch (config.packageSettings().mode()) {
            case WAR, SPRING_BOOT_WAR -> "war";
            case THIN, SPRING_BOOT, QUARKUS, UBER -> "jar";
        };
    }

    private static String rootCoordinate(ProjectConfig config) {
        return config.project().group() + ":" + config.project().name() + ":" + config.project().version();
    }

    private String purlFor(LockPackage lockPackage) {
        return PurlWriter.purl(
                lockPackage.packageId().groupId(),
                lockPackage.packageId().artifactId(),
                lockPackage.version(),
                extension(lockPackage),
                classifier(lockPackage));
    }

    private static Optional<SbomHash> hash(LockPackage lockPackage) {
        Optional<String> sha256 = lockPackage.artifactSha256().or(lockPackage::jarSha256);
        return sha256.filter(value -> !value.isBlank()).map(value -> new SbomHash("SHA-256", value));
    }

    private static String extension(LockPackage lockPackage) {
        if (lockPackage.artifactType().isPresent()) {
            return lockPackage.artifactType().orElseThrow();
        }
        return fileName(lockPackage)
                .map(LockSbomAssembler::extensionOf)
                .orElse("jar");
    }

    private static Optional<String> classifier(LockPackage lockPackage) {
        Optional<String> fileName = fileName(lockPackage);
        if (fileName.isEmpty()) {
            return Optional.empty();
        }
        String artifactId = lockPackage.packageId().artifactId();
        String prefix = artifactId + "-" + lockPackage.version();
        String name = fileName.orElseThrow();
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        if (base.length() > prefix.length() + 1 && base.startsWith(prefix) && base.charAt(prefix.length()) == '-') {
            return Optional.of(base.substring(prefix.length() + 1));
        }
        return Optional.empty();
    }

    private static Optional<String> fileName(LockPackage lockPackage) {
        return lockPackage.artifact().or(lockPackage::jar).map(LockSbomAssembler::lastSegment);
    }

    private static String lastSegment(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1) : "jar";
    }

    private static final class ComponentAccumulator {
        private final SbomComponentType type;
        private final String bomRef;
        private final String group;
        private final String name;
        private final String version;
        private final String purl;
        private final TreeSet<SbomHash> hashes = new TreeSet<>(
                Comparator.comparing(SbomHash::alg).thenComparing(SbomHash::content));
        private SbomComponentScope scope = SbomComponentScope.OPTIONAL;

        private ComponentAccumulator(
                SbomComponentType type,
                String bomRef,
                String group,
                String name,
                String version,
                String purl) {
            this.type = type;
            this.bomRef = bomRef;
            this.group = group;
            this.name = name;
            this.version = version;
            this.purl = purl;
        }

        private void raise(SbomComponentScope candidate) {
            if (candidate == SbomComponentScope.REQUIRED) {
                scope = SbomComponentScope.REQUIRED;
            }
        }

        private String coordinate() {
            return group + ":" + name + ":" + version;
        }

        private SbomComponent toComponent(List<SbomLicense> licenses) {
            return new SbomComponent(type, bomRef, group, name, version, purl, scope, List.copyOf(hashes), licenses);
        }
    }
}
