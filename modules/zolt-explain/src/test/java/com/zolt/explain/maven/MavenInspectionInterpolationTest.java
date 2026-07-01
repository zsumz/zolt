package com.zolt.explain.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the M51 migration-fidelity fixes on the static Maven inspector: {@code ${...}} property
 * interpolation, {@code <exclusions>} parsing, and reading
 * groupId/version/name with parent inheritance.
 */
final class MavenInspectionInterpolationTest {
    @TempDir
    private Path tempDir;

    private final MavenStaticProjectInspector inspector = new MavenStaticProjectInspector();

    //  -------------------------------------------------------------------------------

    @Test
    void interpolatesPropertyDrivenDependencyVersions() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                    <jackson.version>2.17.1</jackson.version>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>${jackson.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenProjectInspection project = inspector.inspect(tempDir).projects().getFirst();
        MavenDependencyInspection jackson = project.dependencies().getFirst();

        assertEquals("2.17.1", jackson.version());
        assertEquals("com.fasterxml.jackson.core:jackson-databind:2.17.1", jackson.coordinate());
    }

    @Test
    void interpolatesNestedPropertyReferencesRecursively() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <base.version>3.14</base.version>
                    <lang3.version>${base.version}.0</lang3.version>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.commons</groupId>
                      <artifactId>commons-lang3</artifactId>
                      <version>${lang3.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenProjectInspection project = inspector.inspect(tempDir).projects().getFirst();

        assertEquals("3.14.0", project.dependencies().getFirst().version());
    }

    @Test
    void interpolatesManagedBomVersionBehindProperty() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <junit.version>5.10.2</junit.version>
                  </properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.junit</groupId>
                        <artifactId>junit-bom</artifactId>
                        <version>${junit.version}</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);

        MavenProjectInspection project = inspector.inspect(tempDir).projects().getFirst();

        assertEquals("5.10.2", project.importedBoms().getFirst().version());
        assertEquals("org.junit:junit-bom:5.10.2", project.importedBoms().getFirst().coordinate());
    }

    @Test
    void propertyDrivenVersionsAreNotFlaggedAsDynamic() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <guava.version>33.2.1-jre</guava.version>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                      <version>${guava.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);

        assertTrue(
                result.signals().stream()
                        .noneMatch(signal -> signal.id().equals("maven.dependency.dynamic-version")),
                () -> "resolved property version must not be a dynamic-version blocker: " + result.signals());
    }

    @Test
    void unresolvablePropertyVersionStaysLiteralAndIsNotFlaggedAsDynamic() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>widget</artifactId>
                      <version>${undeclared.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);
        MavenProjectInspection project = result.projects().getFirst();

        assertEquals("${undeclared.version}", project.dependencies().getFirst().version());
        assertTrue(
                result.signals().stream()
                        .noneMatch(signal -> signal.id().equals("maven.dependency.dynamic-version")),
                () -> "an unresolved property is a review item, not a dynamic-version blocker: "
                        + result.signals());
    }

    @Test
    void genuineDynamicVersionStillFlaggedAfterInterpolationPass() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <range.version>[1.0,2.0)</range.version>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>ranged</artifactId>
                      <version>${range.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenInspectionResult result = inspector.inspect(tempDir);

        assertEquals("[1.0,2.0)", result.projects().getFirst().dependencies().getFirst().version());
        assertTrue(
                result.signals().stream()
                        .anyMatch(signal -> signal.id().equals("maven.dependency.dynamic-version")),
                () -> "a property resolving to a real range must still be flagged: " + result.signals());
    }

    //  -------------------------------------------------------------------------------

    @Test
    void parsesDependencyExclusionsIncludingInterpolatedCoordinates() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <checker.group>org.checkerframework</checker.group>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                      <version>33.2.1-jre</version>
                      <exclusions>
                        <exclusion>
                          <groupId>com.google.code.findbugs</groupId>
                          <artifactId>jsr305</artifactId>
                        </exclusion>
                        <exclusion>
                          <groupId>${checker.group}</groupId>
                          <artifactId>checker-qual</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenProjectInspection project = inspector.inspect(tempDir).projects().getFirst();
        MavenDependencyInspection guava = project.dependencies().getFirst();

        assertEquals(2, guava.exclusions().size());
        assertTrue(guava.exclusions().stream()
                .anyMatch(exclusion -> exclusion.groupId().equals("com.google.code.findbugs")
                        && exclusion.artifactId().equals("jsr305")));
        assertTrue(guava.exclusions().stream()
                .anyMatch(exclusion -> exclusion.groupId().equals("org.checkerframework")
                        && exclusion.artifactId().equals("checker-qual")));
    }

    @Test
    void dependencyWithoutExclusionsHasEmptyExclusionList() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                      <version>33.2.1-jre</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        MavenProjectInspection project = inspector.inspect(tempDir).projects().getFirst();

        assertTrue(project.dependencies().getFirst().exclusions().isEmpty());
    }

    //  -------------------------------------------------------------------------------

    @Test
    void readsExplicitGroupVersionAndName() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme.widgets</groupId>
                  <artifactId>widget-catalog</artifactId>
                  <version>2.3.1</version>
                  <name>Widget Catalog</name>
                </project>
                """);

        MavenProjectInspection project = inspector.inspect(tempDir).projects().getFirst();

        assertEquals("com.acme.widgets", project.groupId());
        assertEquals("2.3.1", project.version());
        assertEquals("widget-catalog", project.name());
        assertEquals("Widget Catalog", project.displayName());
    }

    @Test
    void interpolatesPropertyDrivenProjectVersion() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme.widgets</groupId>
                  <artifactId>widget-catalog</artifactId>
                  <version>${revision}</version>
                  <properties>
                    <revision>4.5.6</revision>
                  </properties>
                </project>
                """);

        MavenProjectInspection project = inspector.inspect(tempDir).projects().getFirst();

        assertEquals("4.5.6", project.version());
    }

    @Test
    void inheritsGroupAndVersionFromParentWhenChildOmitsThem() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.acme.platform</groupId>
                    <artifactId>platform-parent</artifactId>
                    <version>9.9.9</version>
                  </parent>
                  <artifactId>child-module</artifactId>
                </project>
                """);

        MavenProjectInspection project = inspector.inspect(tempDir).projects().getFirst();

        assertEquals("com.acme.platform", project.groupId());
        assertEquals("9.9.9", project.version());
        assertEquals("child-module", project.name());
    }

    @Test
    void leavesGroupAndVersionBlankWhenGenuinelyAbsent() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <artifactId>bare</artifactId>
                </project>
                """);

        MavenProjectInspection project = inspector.inspect(tempDir).projects().getFirst();

        assertEquals("", project.groupId());
        assertEquals("", project.version());
        assertEquals("", project.displayName());
    }
}
