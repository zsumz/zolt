package sh.zolt.explain.emit;

import sh.zolt.explain.gradle.GradleDependencyInspection;
import sh.zolt.explain.gradle.GradleProjectInspection;
import sh.zolt.project.BomSettings;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.CompilerSettings;
import sh.zolt.project.NativeSettings;
import sh.zolt.project.PackageMode;
import sh.zolt.project.PackageSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.project.PublicationMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Drafts a {@code [bom]} member from a Gradle {@code java-platform} project: {@code platform(...)}
 * imports become {@code [bom.imports]} and {@code constraints { }} pins become {@code [bom.versions]}.
 * Plain dependencies (a {@code java-platform} with {@code allowDependencies()}) become a review note,
 * because a Zolt BOM carries no dependencies. Mirrors {@link MavenBomDraftMapper}.
 */
final class GradleBomDraftMapper {
    private GradleBomDraftMapper() {
    }

    static boolean isBom(GradleProjectInspection primary) {
        return primary.plugins().stream().anyMatch(plugin -> "java-platform".equals(plugin.id()));
    }

    static DraftZoltToml map(GradleProjectInspection primary, List<String> notes) {
        Set<String> commentedProjectKeys = new TreeSet<>();

        List<BomSettings.ImportedBom> imports = new ArrayList<>();
        for (GradleDependencyInspection dependency : primary.dependencies()) {
            if (!dependency.isPlatform()) {
                notePlainDependency(dependency, notes);
                continue;
            }
            String resolved = dependency.resolvedCoordinate();
            if (resolved == null || resolved.isBlank()) {
                notes.add("Gradle java-platform import in `" + dependency.configuration() + "` (`"
                        + dependency.notation() + "`) did not resolve to a coordinate; add it under"
                        + " [bom.imports] by hand.");
                continue;
            }
            String coordinate = GradleInspectionMapper.coordinateOf(resolved);
            String version = GradleInspectionMapper.versionOf(resolved);
            if (unusableBomVersion(coordinate, version, "[bom.imports]", notes)) {
                continue;
            }
            imports.add(new BomSettings.ImportedBom(coordinate, version, null));
            if (dependency.platformKind() == GradleDependencyInspection.PlatformKind.ENFORCED_PLATFORM) {
                notes.add("Gradle `enforcedPlatform(" + coordinate + ")` was recorded as a [bom.imports]"
                        + " entry; a published BOM composes imports without Gradle's enforced-override"
                        + " semantics, so review whether this coordinate belongs in [bom.versions] instead.");
            }
        }

        List<BomSettings.ManagedVersion> versions = new ArrayList<>();
        for (GradleDependencyInspection constraint : primary.constraints()) {
            String resolved = constraint.resolvedCoordinate();
            if (resolved == null || resolved.isBlank()) {
                notes.add("Gradle java-platform constraint in `" + constraint.configuration() + "` (`"
                        + constraint.notation() + "`) did not resolve to a coordinate; add it under"
                        + " [bom.versions] by hand.");
                continue;
            }
            String coordinate = GradleInspectionMapper.coordinateOf(resolved);
            String version = GradleInspectionMapper.versionOf(resolved);
            if (unusableBomVersion(coordinate, version, "[bom.versions]", notes)) {
                continue;
            }
            versions.add(new BomSettings.ManagedVersion(coordinate, version, null, null, null));
        }

        BomSettings bomSettings = new BomSettings(BomSettings.Members.none(), versions, imports);
        ProjectMetadata metadata = new ProjectMetadata(
                primary.name(),
                GradleInspectionMapper.emittedVersion(primary),
                GradleInspectionMapper.emittedGroup(primary),
                GradleInspectionMapper.javaVersion(primary.javaVersion(), notes, commentedProjectKeys),
                Optional.empty());
        GradleInspectionMapper.addCoordinatePlaceholderNotes(primary, notes);
        PackageSettings packageSettings = new PackageSettings(
                        PackageMode.BOM, false, false, false, PublicationMetadata.empty())
                .withBom(bomSettings);
        ProjectConfig config = ProjectConfigs.withAllDependencySections(
                metadata,
                ProjectConfig.defaultRepositories(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                NativeSettings.defaults(),
                CompilerSettings.defaults(),
                packageSettings);
        notes.add("Drafted a [bom] member from a Gradle java-platform project: platform() imports became"
                + " [bom.imports] and constraints became [bom.versions]. Review the pins and set members if"
                + " this BOM should manage a Zolt workspace family.");
        return new DraftZoltToml(config, notes, List.copyOf(commentedProjectKeys), false);
    }

    private static void notePlainDependency(GradleDependencyInspection dependency, List<String> notes) {
        String resolved = dependency.resolvedCoordinate();
        String coordinate = resolved == null || resolved.isBlank()
                ? dependency.notation()
                : GradleInspectionMapper.coordinateOf(resolved);
        notes.add("Gradle java-platform project declares dependency `" + coordinate + "` in `"
                + dependency.configuration() + "`; a Zolt BOM publishes only a curated version set and"
                + " carries no dependencies. Move it to the consuming module, or pin it under [bom.versions].");
    }

    private static boolean unusableBomVersion(
            String coordinate, String version, String section, List<String> notes) {
        if (version == null || version.isBlank() || version.contains("${")) {
            notes.add("BOM entry `" + coordinate + "` has an unresolved version `" + version
                    + "`; add it under " + section + " with a fixed version before publishing.");
            return true;
        }
        return false;
    }
}
