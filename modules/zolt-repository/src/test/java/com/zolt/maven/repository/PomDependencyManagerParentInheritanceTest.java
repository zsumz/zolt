package com.zolt.maven.repository;

import static com.zolt.maven.repository.PomDependencyManagerTestSupport.effective;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class PomDependencyManagerParentInheritanceTest {
    private final RawPomParser parser = new RawPomParser();
    private final PomDependencyManager manager = new PomDependencyManager();

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
}
