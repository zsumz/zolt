package sh.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.explain.maven.MavenDependencyExclusion;
import sh.zolt.explain.maven.MavenDependencyInspection;
import sh.zolt.project.DependencyConstraint;
import sh.zolt.project.DependencyConstraintKind;
import sh.zolt.project.DependencyMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

final class MavenDependencyMapperEdgeTest {
    @Test
    void sectionMapperNotesUnsupportedScopesAndKeepsMappableEdges() {
        WorkspaceMemberRegistry registry = new WorkspaceMemberRegistry();
        registry.register("com.acme:lib", "lib");
        Map<String, String> dependencies = new TreeMap<>();
        Map<String, String> runtime = new TreeMap<>();
        Map<String, String> provided = new TreeMap<>();
        Map<String, String> test = new TreeMap<>();
        Map<String, String> workspaceDependencies = new TreeMap<>();
        Map<String, String> workspaceTest = new TreeMap<>();
        Set<String> managedDependencies = new TreeSet<>();
        Set<String> managedRuntime = new TreeSet<>();
        Set<String> managedProvided = new TreeSet<>();
        Set<String> managedTest = new TreeSet<>();
        Map<String, DependencyMetadata> metadata = new TreeMap<>();
        List<String> notes = new ArrayList<>();
        MavenDependencySectionMapper mapper = new MavenDependencySectionMapper(
                registry,
                new MavenDependencySectionMapper.VersionedSections(
                        dependencies,
                        runtime,
                        provided,
                        test,
                        workspaceDependencies,
                        workspaceTest),
                new MavenDependencySectionMapper.ManagedSections(
                        managedDependencies,
                        managedRuntime,
                        managedProvided,
                        managedTest),
                true,
                Map.of("com.acme:pinned", "1.2.3"),
                metadata,
                notes);

        mapper.map(dependency("runtime", "com.acme:pinned", ""));
        mapper.map(dependency(
                "provided",
                "com.acme:managed",
                "",
                List.of(new MavenDependencyExclusion("org.legacy", "legacy-api"))));
        mapper.map(dependency("compile", "com.acme:lib", "1.0.0"));
        mapper.map(dependency("test", "com.acme:lib", "1.0.0"));
        mapper.map(dependency("runtime", "com.acme:lib", "1.0.0"));
        mapper.map(dependency("integrationTest", "com.acme:custom", "1.0.0"));
        mapper.map(dependency("integrationTest", "com.acme:managed-custom", ""));
        mapper.map(dependency("compile", "com.acme:placeholder", "${revision}"));

        assertEquals("1.2.3", runtime.get("com.acme:pinned"));
        assertTrue(managedProvided.contains("com.acme:managed"), () -> managedProvided.toString());
        assertTrue(metadata.containsKey(DependencyMetadata.key("provided.dependencies", "com.acme:managed")),
                () -> metadata.toString());
        assertEquals("lib", workspaceDependencies.get("com.acme:lib"));
        assertEquals("lib", workspaceTest.get("com.acme:lib"));
        assertFalse(runtime.containsKey("com.acme:lib"), () -> runtime.toString());
        assertTrue(notes.stream().anyMatch(note -> note.contains("Maven scope `runtime`")
                && note.contains("cannot express as a workspace edge")), () -> notes.toString());
        assertTrue(notes.stream().anyMatch(note -> note.contains("Maven scope `integrationTest`")
                && note.contains("has no direct Zolt section")), () -> notes.toString());
        assertTrue(notes.stream().anyMatch(note -> note.contains("managed-custom")
                && note.contains("no direct Zolt platform-managed section")), () -> notes.toString());
        assertTrue(notes.stream().anyMatch(note -> note.contains("placeholder")
                && note.contains("property the static audit could not resolve")), () -> notes.toString());
    }

    @Test
    void constraintMapperSkipsDirectCoordinatesAndNotesUnmappableManagedEntries() {
        List<String> notes = new ArrayList<>();
        Map<String, DependencyConstraint> constraints = MavenDependencyConstraintMapper.map(
                List.of(
                        dependency("compile", "com.acme:direct", "1.0.0"),
                        dependency("compile", "com.acme:classifier", "1.0.0", "jar", "tests"),
                        dependency("compile", "com.acme:test-jar", "1.0.0", "test-jar", ""),
                        dependency("compile", "com.acme:property", "${revision}"),
                        dependency("compile", "com.acme:blank", ""),
                        dependency("compile", "com.acme:constraint", "2.0.0")),
                List.of(dependency("compile", "com.acme:direct", "1.0.0")),
                notes);

        assertEquals(Set.of("com.acme:constraint"), constraints.keySet());
        DependencyConstraint constraint = constraints.get("com.acme:constraint");
        assertEquals("2.0.0", constraint.version());
        assertEquals(DependencyConstraintKind.STRICT, constraint.kind());
        assertEquals("Imported from Maven dependencyManagement.", constraint.reason().orElseThrow());
        assertTrue(notes.stream().anyMatch(note -> note.contains("com.acme:classifier")
                && note.contains("classifier `tests`")), () -> notes.toString());
        assertTrue(notes.stream().anyMatch(note -> note.contains("com.acme:test-jar")
                && note.contains("Maven type `test-jar`")), () -> notes.toString());
        assertTrue(notes.stream().anyMatch(note -> note.contains("com.acme:property")
                && note.contains("property the static audit could not resolve")), () -> notes.toString());
        assertFalse(notes.stream().anyMatch(note -> note.contains("com.acme:direct")), () -> notes.toString());
        assertFalse(notes.stream().anyMatch(note -> note.contains("com.acme:blank")), () -> notes.toString());
    }

    private static MavenDependencyInspection dependency(String scope, String coordinate, String version) {
        return dependency(scope, coordinate, version, "jar", "");
    }

    private static MavenDependencyInspection dependency(
            String scope,
            String coordinate,
            String version,
            List<MavenDependencyExclusion> exclusions) {
        return new MavenDependencyInspection(
                scope,
                coordinate,
                version,
                "jar",
                false,
                false,
                false,
                true,
                "",
                exclusions);
    }

    private static MavenDependencyInspection dependency(
            String scope,
            String coordinate,
            String version,
            String type,
            String classifier) {
        return new MavenDependencyInspection(
                scope,
                coordinate,
                version,
                type,
                false,
                false,
                false,
                true,
                classifier,
                List.of());
    }
}
