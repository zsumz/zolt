package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.maven.Coordinate;
import com.zolt.maven.EffectiveRawPom;
import com.zolt.maven.RawPom;
import com.zolt.maven.RawPomDependency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class EffectivePomInheritanceBuilderTest {
    private final EffectivePomInheritanceBuilder builder = new EffectivePomInheritanceBuilder();

    @Test
    void childValuesOverrideNearestParentAndParentPropertiesAccumulateRootFirst() {
        RawPom root = pom(
                "com.root",
                "root-parent",
                "1.0.0",
                Map.of("root", "root-value", "override", "root"),
                List.of(dependency("com.root", "root-lib", "1.0.0")));
        RawPom nearest = pom(
                "com.nearest",
                "nearest-parent",
                "2.0.0",
                Map.of("nearest", "nearest-value", "override", "nearest"),
                List.of(dependency("com.nearest", "nearest-lib", "2.0.0")));
        RawPom child = pom(
                null,
                "child",
                null,
                Map.of("child", "child-value", "override", "child"),
                List.of(dependency("com.child", "child-lib", "3.0.0")));

        EffectiveRawPom effective = builder.build(
                new Coordinate("com.requested", "child", Optional.of("9.0.0")),
                child,
                List.of(root, nearest));

        assertEquals("com.nearest", effective.groupId());
        assertEquals("2.0.0", effective.version());
        assertEquals(Map.of(
                "root", "root-value",
                "nearest", "nearest-value",
                "child", "child-value",
                "override", "child"), effective.properties());
        assertEquals(List.of(
                dependency("com.root", "root-lib", "1.0.0"),
                dependency("com.nearest", "nearest-lib", "2.0.0"),
                dependency("com.child", "child-lib", "3.0.0")), effective.dependencyManagement());
    }

    @Test
    void requestCoordinateSuppliesMissingGroupAndVersion() {
        RawPom child = pom(null, "child", null, Map.of(), List.of());

        EffectiveRawPom effective = builder.build(
                new Coordinate("com.requested", "child", Optional.of("9.0.0")),
                child,
                List.of());

        assertEquals("com.requested", effective.groupId());
        assertEquals("9.0.0", effective.version());
    }

    private static RawPom pom(
            String groupId,
            String artifactId,
            String version,
            Map<String, String> properties,
            List<RawPomDependency> dependencyManagement) {
        return new RawPom(
                Optional.ofNullable(groupId),
                artifactId,
                Optional.ofNullable(version),
                "pom",
                Optional.empty(),
                Optional.empty(),
                properties,
                dependencyManagement,
                List.of());
    }

    private static RawPomDependency dependency(String groupId, String artifactId, String version) {
        return new RawPomDependency(
                groupId,
                artifactId,
                Optional.of(version),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of());
    }
}
