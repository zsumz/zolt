package sh.zolt.maven.repository;

import static sh.zolt.maven.repository.PomDependencyManagerTestSupport.effective;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PomDependencyManagerTest {
    private final RawPomParser parser = new RawPomParser();
    private final PomDependencyManager manager = new PomDependencyManager();

    @Test
    void dependencyMissingVersionInheritsManagedVersion() {
        EffectiveRawPom pom = effective(parser, """
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
        EffectiveRawPom pom = effective(parser, """
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
    void managedVersionCanUseInterpolatedProperty() {
        EffectiveRawPom pom = effective(parser, """
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
    void unusedManagedEntryWithUnresolvedVersionDoesNotFail() {
        EffectiveRawPom pom = effective(parser, """
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
                      <dependency>
                        <groupId>org.apache.hbase</groupId>
                        <artifactId>hbase-client</artifactId>
                        <version>${hbase.version}</version>
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
    void usedManagedEntryWithUnresolvedVersionStillFails() {
        EffectiveRawPom pom = effective(parser, """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.apache.hbase</groupId>
                        <artifactId>hbase-client</artifactId>
                        <version>${hbase.version}</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.hbase</groupId>
                      <artifactId>hbase-client</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        PomInterpolationException exception = assertThrows(
                PomInterpolationException.class,
                () -> manager.applyManagedVersions(pom));

        assertTrue(exception.getMessage().contains("${hbase.version}"));
    }

    @Test
    void managedScopeAppliesBeforeTransitiveFiltering() {
        EffectiveRawPom pom = effective(parser, """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.13.2</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>junit</groupId>
                      <artifactId>junit</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        RawPomDependency dependency = manager.applyManagedVersion(pom.rawPom().dependencies().getFirst(), pom);

        assertEquals("4.13.2", dependency.version().orElseThrow());
        assertEquals("test", dependency.scope().orElseThrow());
        assertTrue(manager.applyManagedVersions(pom).isEmpty());
    }

    @Test
    void unmatchedDependencyRemainsVersionlessForLaterErrorHandling() {
        EffectiveRawPom pom = effective(parser, """
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
}
