package sh.zolt.explain.emit;

import sh.zolt.explain.maven.MavenDependencyExclusion;
import sh.zolt.explain.maven.MavenDependencyInspection;
import sh.zolt.project.DependencyExclusionSpec;
import sh.zolt.project.DependencyMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MavenDependencySectionMapper {
    record VersionedSections(
            Map<String, String> dependencies,
            Map<String, String> runtime,
            Map<String, String> provided,
            Map<String, String> test,
            Map<String, String> workspaceDependencies,
            Map<String, String> workspaceTest) {
    }

    record ManagedSections(
            Set<String> dependencies,
            Set<String> runtime,
            Set<String> provided,
            Set<String> test) {
    }

    private final WorkspaceMemberRegistry registry;
    private final VersionedSections versioned;
    private final ManagedSections managed;
    private final boolean platformAvailable;
    private final Map<String, String> managedPins;
    private final Map<String, DependencyMetadata> dependencyMetadata;
    private final List<String> notes;

    MavenDependencySectionMapper(
            WorkspaceMemberRegistry registry,
            VersionedSections versioned,
            ManagedSections managed,
            boolean platformAvailable,
            Map<String, String> managedPins,
            Map<String, DependencyMetadata> dependencyMetadata,
            List<String> notes) {
        this.registry = registry;
        this.versioned = versioned;
        this.managed = managed;
        this.platformAvailable = platformAvailable;
        this.managedPins = managedPins == null ? Map.of() : Map.copyOf(managedPins);
        this.dependencyMetadata = dependencyMetadata;
        this.notes = notes;
    }

    void map(MavenDependencyInspection dependency) {
        String coordinate = coordinateOf(dependency.coordinate());
        if (!dependency.classifier().isBlank()) {
            notes.add(
                    "Dependency `" + coordinate + "` (scope " + dependency.scope()
                            + ") declares Maven classifier `" + dependency.classifier()
                            + "`. Zolt does not model classifier artifacts in zolt.toml yet; add the"
                            + " correct artifact manually before resolving.");
            return;
        }
        if (mapWorkspaceDependency(dependency, coordinate)) {
            return;
        }
        String version = dependency.version();
        if (version.isBlank()) {
            String pinnedVersion = managedPins.get(coordinate);
            if (pinnedVersion != null) {
                String section = mapVersionedDependency(dependency, coordinate, pinnedVersion);
                if (section != null) {
                    recordExclusions(section, coordinate, dependency);
                }
                return;
            }
            String managedSection = mapManagedDependency(dependency, coordinate);
            if (managedSection != null) {
                recordExclusions(managedSection, coordinate, dependency);
                return;
            }
            notes.add(
                    "Dependency `" + coordinate + "` (scope " + dependency.scope() + ") has no static"
                            + " version; it is likely managed by a BOM. Add a version or platform entry"
                            + " before resolving.");
            return;
        }
        if (version.contains("${")) {
            notes.add(
                    "Dependency `" + coordinate + "` (scope " + dependency.scope() + ") uses version `"
                            + version + "`, which references a property the static audit could not"
                            + " resolve. Replace it with a fixed version before resolving.");
            return;
        }
        String section = mapVersionedDependency(dependency, coordinate, version);
        if (section != null) {
            recordExclusions(section, coordinate, dependency);
        }
    }

    private String mapVersionedDependency(
            MavenDependencyInspection dependency,
            String coordinate,
            String version) {
        return switch (dependency.scope()) {
            case "compile" -> {
                versioned.dependencies().put(coordinate, version);
                yield "dependencies";
            }
            case "runtime" -> {
                versioned.runtime().put(coordinate, version);
                yield "runtime.dependencies";
            }
            case "provided" -> {
                versioned.provided().put(coordinate, version);
                yield "provided.dependencies";
            }
            case "test" -> {
                versioned.test().put(coordinate, version);
                yield "test.dependencies";
            }
            default -> {
                notes.add(
                        "Dependency `" + coordinate + "` uses Maven scope `" + dependency.scope()
                                + "`, which has no direct Zolt section; place it manually after review.");
                yield null;
            }
        };
    }

    private String mapManagedDependency(MavenDependencyInspection dependency, String coordinate) {
        if (!platformAvailable) {
            return null;
        }
        return switch (dependency.scope()) {
            case "compile" -> {
                managed.dependencies().add(coordinate);
                yield "dependencies";
            }
            case "runtime" -> {
                managed.runtime().add(coordinate);
                yield "runtime.dependencies";
            }
            case "provided" -> {
                managed.provided().add(coordinate);
                yield "provided.dependencies";
            }
            case "test" -> {
                managed.test().add(coordinate);
                yield "test.dependencies";
            }
            default -> {
                notes.add(
                        "Dependency `" + coordinate + "` has no static version and uses Maven scope `"
                                + dependency.scope()
                                + "`, which has no direct Zolt platform-managed section; place it manually"
                                + " after review.");
                yield null;
            }
        };
    }

    private boolean mapWorkspaceDependency(MavenDependencyInspection dependency, String coordinate) {
        if (registry == null) {
            return false;
        }
        String memberPath = registry.pathFor(coordinate);
        if (memberPath == null) {
            return false;
        }
        switch (dependency.scope()) {
            case "compile" -> versioned.workspaceDependencies().put(coordinate, memberPath);
            case "test" -> versioned.workspaceTest().put(coordinate, memberPath);
            default -> notes.add(
                    "Dependency `" + coordinate + "` targets sibling module `" + memberPath
                            + "` in Maven scope `" + dependency.scope() + "`, which Zolt cannot express"
                            + " as a workspace edge; wire it under the matching section by hand.");
        }
        return true;
    }

    private void recordExclusions(
            String section,
            String coordinate,
            MavenDependencyInspection dependency) {
        if (dependency.exclusions().isEmpty()) {
            return;
        }
        List<DependencyExclusionSpec> specs = new ArrayList<>();
        for (MavenDependencyExclusion exclusion : dependency.exclusions()) {
            specs.add(new DependencyExclusionSpec(exclusion.groupId(), exclusion.artifactId()));
        }
        DependencyMetadata metadata = new DependencyMetadata(
                section,
                coordinate,
                null,
                dependency.managed(),
                null,
                false,
                false,
                specs);
        dependencyMetadata.put(DependencyMetadata.key(section, coordinate), metadata);
    }

    private static String coordinateOf(String coordinate) {
        String[] parts = coordinate.split(":");
        if (parts.length >= 2) {
            return parts[0] + ":" + parts[1];
        }
        return coordinate;
    }
}
