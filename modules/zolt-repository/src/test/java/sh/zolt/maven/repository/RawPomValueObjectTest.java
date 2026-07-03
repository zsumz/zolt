package sh.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class RawPomValueObjectTest {
    @Test
    void rawPomNormalizesNullableOptionalsAndTakesDefensiveCopies() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("revision", "1.0.0");
        List<RawPomDependency> dependencyManagement = new ArrayList<>();
        dependencyManagement.add(dependency("managed-api"));
        List<RawPomDependency> dependencies = new ArrayList<>();
        dependencies.add(dependency("runtime-api"));

        RawPom pom = new RawPom(
                null,
                "demo",
                null,
                "jar",
                null,
                null,
                properties,
                dependencyManagement,
                dependencies);

        properties.put("late", "2.0.0");
        dependencyManagement.clear();
        dependencies.clear();

        assertFalse(pom.groupId().isPresent());
        assertFalse(pom.version().isPresent());
        assertFalse(pom.parent().isPresent());
        assertFalse(pom.relocation().isPresent());
        assertEquals(Map.of("revision", "1.0.0"), pom.properties());
        assertEquals(List.of(dependency("managed-api")), pom.dependencyManagement());
        assertEquals(List.of(dependency("runtime-api")), pom.dependencies());
        assertThrows(UnsupportedOperationException.class, () -> pom.dependencies().add(dependency("late-api")));
    }

    @Test
    void rawPomDependencyNormalizesNullableOptionalsAndCopiesExclusions() {
        List<RawPomExclusion> exclusions = new ArrayList<>();
        exclusions.add(new RawPomExclusion("org.unwanted", "legacy-helper"));

        RawPomDependency dependency = new RawPomDependency(
                "org.example",
                "demo",
                null,
                null,
                null,
                null,
                true,
                exclusions);

        exclusions.add(new RawPomExclusion("org.late", "ignored"));

        assertFalse(dependency.version().isPresent());
        assertFalse(dependency.scope().isPresent());
        assertFalse(dependency.type().isPresent());
        assertFalse(dependency.classifier().isPresent());
        assertEquals(List.of(new RawPomExclusion("org.unwanted", "legacy-helper")), dependency.exclusions());
        assertThrows(
                UnsupportedOperationException.class,
                () -> dependency.exclusions().add(new RawPomExclusion("org.extra", "ignored")));
    }

    @Test
    void rawPomRelocationNormalizesNullableOptionals() {
        RawPomRelocation relocation = new RawPomRelocation(null, null, null, null);

        assertFalse(relocation.groupId().isPresent());
        assertFalse(relocation.artifactId().isPresent());
        assertFalse(relocation.version().isPresent());
        assertFalse(relocation.message().isPresent());
    }

    @Test
    void rawPomParentNormalizesNullableRelativePath() {
        RawPomParent parent = new RawPomParent("org.example", "parent", "1.0.0", null);

        assertEquals("org.example", parent.groupId());
        assertEquals("parent", parent.artifactId());
        assertEquals("1.0.0", parent.version());
        assertFalse(parent.relativePath().isPresent());
    }

    private static RawPomDependency dependency(String artifactId) {
        return new RawPomDependency(
                "org.example",
                artifactId,
                Optional.of("1.0.0"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of());
    }
}
