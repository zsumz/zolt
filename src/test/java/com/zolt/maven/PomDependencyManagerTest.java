package com.zolt.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class PomDependencyManagerTest {
    private final RawPomParser parser = new RawPomParser();
    private final PomDependencyManager manager = new PomDependencyManager();

    @Test
    void dependencyMissingVersionInheritsManagedVersion() {
        EffectiveRawPom pom = effective("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.slf4j</groupId>
                        <artifactId>slf4j-api</artifactId>
                        <version>2.0.16</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.slf4j</groupId>
                      <artifactId>slf4j-api</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        RawPomDependency dependency = manager.applyManagedVersions(pom).getFirst();

        assertEquals("2.0.16", dependency.version().orElseThrow());
    }

    @Test
    void directDependencyVersionOverridesManagedVersion() {
        EffectiveRawPom pom = effective("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.slf4j</groupId>
                        <artifactId>slf4j-api</artifactId>
                        <version>2.0.16</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.slf4j</groupId>
                      <artifactId>slf4j-api</artifactId>
                      <version>2.0.15</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        RawPomDependency dependency = manager.applyManagedVersions(pom).getFirst();

        assertEquals("2.0.15", dependency.version().orElseThrow());
    }

    @Test
    void parentDependencyManagementAppliesToChildDependencies() {
        RawPom parent = parser.parse("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter</artifactId>
                        <version>5.11.4</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
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
                  <dependencies>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
        EffectiveRawPom effective = new ParentPomResolver(coordinate -> parent).resolve(child);

        RawPomDependency dependency = manager.applyManagedVersions(effective).getFirst();

        assertEquals("5.11.4", dependency.version().orElseThrow());
    }

    @Test
    void childManagedVersionOverridesParentManagedVersion() {
        RawPom parent = parser.parse("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.slf4j</groupId>
                        <artifactId>slf4j-api</artifactId>
                        <version>2.0.15</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
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
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.slf4j</groupId>
                        <artifactId>slf4j-api</artifactId>
                        <version>2.0.16</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.slf4j</groupId>
                      <artifactId>slf4j-api</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
        EffectiveRawPom effective = new ParentPomResolver(coordinate -> parent).resolve(child);

        RawPomDependency dependency = manager.applyManagedVersions(effective).getFirst();

        assertEquals("2.0.16", dependency.version().orElseThrow());
    }

    @Test
    void managedVersionCanUseInterpolatedProperty() {
        EffectiveRawPom pom = effective("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <slf4j.version>2.0.16</slf4j.version>
                  </properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.slf4j</groupId>
                        <artifactId>slf4j-api</artifactId>
                        <version>${slf4j.version}</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.slf4j</groupId>
                      <artifactId>slf4j-api</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        RawPomDependency dependency = manager.applyManagedVersions(pom).getFirst();

        assertEquals("2.0.16", dependency.version().orElseThrow());
    }

    @Test
    void unmatchedDependencyRemainsVersionlessForLaterErrorHandling() {
        EffectiveRawPom pom = effective("""
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.slf4j</groupId>
                      <artifactId>slf4j-api</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        RawPomDependency dependency = manager.applyManagedVersions(pom).getFirst();

        assertTrue(dependency.version().isEmpty());
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
