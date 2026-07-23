package sh.zolt.explain.emit;

import sh.zolt.explain.maven.MavenDependencyInspection;
import sh.zolt.explain.maven.MavenProjectInspection;
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
 * Drafts a {@code [bom]} member from a standalone Maven {@code dependencyManagement} POM: import-scope
 * BOMs become {@code [bom.imports]} and plain pins become {@code [bom.versions]}.
 */
final class MavenBomDraftMapper {
    private MavenBomDraftMapper() {
    }

    static boolean isBom(MavenProjectInspection primary) {
        return "pom".equals(primary.packaging())
                && primary.modules().isEmpty()
                && primary.sourceRoots().isEmpty()
                && (!primary.dependencyManagement().isEmpty() || !primary.importedBoms().isEmpty());
    }

    static DraftZoltToml map(MavenProjectInspection primary, List<String> notes) {
        Set<String> commentedProjectKeys = new TreeSet<>();
        List<BomSettings.ImportedBom> imports = new ArrayList<>();
        for (MavenDependencyInspection bom : primary.importedBoms()) {
            String coordinate = MavenInspectionMapper.coordinateOf(bom.coordinate());
            if (unusableBomVersion(coordinate, bom.version(), "[bom.imports]", notes)) {
                continue;
            }
            imports.add(new BomSettings.ImportedBom(coordinate, bom.version(), null));
        }
        List<BomSettings.ManagedVersion> versions = new ArrayList<>();
        for (MavenDependencyInspection pin : primary.dependencyManagement()) {
            String coordinate = MavenInspectionMapper.coordinateOf(pin.coordinate());
            if (unusableBomVersion(coordinate, pin.version(), "[bom.versions]", notes)) {
                continue;
            }
            versions.add(new BomSettings.ManagedVersion(coordinate, pin.version(), null, pin.classifier(), null));
        }
        BomSettings bomSettings = new BomSettings(BomSettings.Members.none(), versions, imports);
        ProjectMetadata metadata = new ProjectMetadata(
                primary.artifactId(),
                MavenInspectionMapper.version(primary, notes),
                MavenInspectionMapper.group(primary, notes),
                MavenInspectionMapper.javaVersion(primary.javaVersion(), notes, commentedProjectKeys),
                Optional.empty());
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
        notes.add("Drafted a [bom] member from Maven dependencyManagement: import-scope BOMs became"
                + " [bom.imports] and plain pins became [bom.versions]. Review the pins and set members if this"
                + " BOM should manage a Zolt workspace family.");
        return new DraftZoltToml(config, notes, List.copyOf(commentedProjectKeys), false);
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
