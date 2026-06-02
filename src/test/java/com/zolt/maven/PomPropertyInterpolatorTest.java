package com.zolt.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PomPropertyInterpolatorTest {
    private final RawPomParser parser = new RawPomParser();
    private final PomPropertyInterpolator interpolator = new PomPropertyInterpolator();

    @Test
    void supportsProjectVersion() {
        EffectiveRawPom pom = effective("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.2.3</version>
                </project>
                """);

        assertEquals("1.2.3", interpolator.interpolate("${project.version}", pom));
    }

    @Test
    void supportsProjectGroupId() {
        EffectiveRawPom pom = effective("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.2.3</version>
                </project>
                """);

        assertEquals("com.example", interpolator.interpolate("${project.groupId}", pom));
    }

    @Test
    void supportsProjectArtifactId() {
        EffectiveRawPom pom = effective("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.2.3</version>
                </project>
                """);

        assertEquals("app-tests", interpolator.interpolate("${project.artifactId}-tests", pom));
    }

    @Test
    void supportsCustomPropertiesFromParentAndChild() {
        RawPom parent = parser.parse("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <junit.version>5.11.4</junit.version>
                    <shared.version>parent</shared.version>
                  </properties>
                </project>
                """);
        RawPom child = parser.parse("""
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                  <properties>
                    <shared.version>child</shared.version>
                  </properties>
                </project>
                """);
        EffectiveRawPom pom = new ParentPomResolver(coordinate -> parent).resolve(child);

        assertEquals("5.11.4", interpolator.interpolate("${junit.version}", pom));
        assertEquals("child", interpolator.interpolate("${shared.version}", pom));
    }

    @Test
    void interpolatesDependencyFields() {
        EffectiveRawPom pom = effective("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.2.3</version>
                  <properties>
                    <dep.version>2.0.0</dep.version>
                  </properties>
                </project>
                """);
        RawPomDependency dependency = new RawPomDependency(
                "${project.groupId}",
                "${project.artifactId}-api",
                Optional.of("${dep.version}"),
                Optional.of("compile"),
                Optional.empty(),
                Optional.empty(),
                false,
                java.util.List.of());

        RawPomDependency interpolated = interpolator.interpolateDependency(dependency, pom);

        assertEquals("com.example", interpolated.groupId());
        assertEquals("app-api", interpolated.artifactId());
        assertEquals("2.0.0", interpolated.version().orElseThrow());
    }

    @Test
    void unresolvedPropertyProducesActionableErrorWithCoordinateContext() {
        EffectiveRawPom pom = effective("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.2.3</version>
                </project>
                """);

        PomInterpolationException exception = assertThrows(
                PomInterpolationException.class,
                () -> interpolator.interpolate("${missing.version}", pom));

        assertEquals(
                "Unresolved POM property `${missing.version}` while processing com.example:app:1.2.3. Define the property or declare the value explicitly.",
                exception.getMessage());
    }

    private EffectiveRawPom effective(String xml) {
        RawPom rawPom = parser.parse(xml);
        return new EffectiveRawPom(
                rawPom,
                java.util.List.of(),
                rawPom.groupId().orElseThrow(),
                rawPom.version().orElseThrow(),
                Map.copyOf(rawPom.properties()),
                rawPom.dependencyManagement());
    }
}
