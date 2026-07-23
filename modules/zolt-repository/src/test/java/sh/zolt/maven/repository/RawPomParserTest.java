package sh.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class RawPomParserTest {
    private final RawPomParser parser = new RawPomParser();

    @Test
    void parsesRawGuavaFixture() throws IOException {
        RawPom pom = parseFixture("guava-33.4.0-jre.pom");

        assertFalse(pom.groupId().isPresent());
        assertEquals("guava", pom.artifactId());
        assertFalse(pom.version().isPresent());
        assertEquals("bundle", pom.packaging());

        RawPomParent parent = pom.parent().orElseThrow();
        assertEquals("com.google.guava", parent.groupId());
        assertEquals("guava-parent", parent.artifactId());
        assertEquals("33.4.0-jre", parent.version());

        assertTrue(pom.properties().isEmpty());
        assertTrue(pom.dependencyManagement().isEmpty());

        assertEquals(6, pom.dependencies().size());
        RawPomDependency failureAccess = pom.dependencies().getFirst();
        assertEquals("com.google.guava", failureAccess.groupId());
        assertEquals("failureaccess", failureAccess.artifactId());
        assertEquals("1.0.2", failureAccess.version().orElseThrow());

        RawPomDependency jsr305 = pom.dependencies().get(2);
        assertEquals("com.google.code.findbugs", jsr305.groupId());
        assertEquals("jsr305", jsr305.artifactId());
        assertFalse(jsr305.version().isPresent());
    }

    @Test
    void parsesRawJunitFixtureWithDependencyManagement() throws IOException {
        RawPom pom = parseFixture("junit-jupiter-5.11.4.pom");

        assertEquals("org.junit.jupiter", pom.groupId().orElseThrow());
        assertEquals("junit-jupiter", pom.artifactId());
        assertEquals("5.11.4", pom.version().orElseThrow());
        assertEquals("jar", pom.packaging());
        assertEquals(1, pom.dependencyManagement().size());
        RawPomDependency bom = pom.dependencyManagement().getFirst();
        assertEquals("org.junit", bom.groupId());
        assertEquals("junit-bom", bom.artifactId());
        assertEquals("pom", bom.type().orElseThrow());
        assertEquals("import", bom.scope().orElseThrow());
        assertEquals(3, pom.dependencies().size());
        assertEquals("runtime", pom.dependencies().get(2).scope().orElseThrow());
    }

    @Test
    void parsesPropertiesOptionalAndExclusions() {
        RawPom pom = parser.parse("""
                <project>
                  <artifactId>example</artifactId>
                  <properties>
                    <truth.version>1.4.2</truth.version>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.errorprone</groupId>
                      <artifactId>error_prone_annotations</artifactId>
                      <version>2.36.0</version>
                      <scope>provided</scope>
                      <optional>true</optional>
                      <exclusions>
                        <exclusion>
                          <groupId>com.example</groupId>
                          <artifactId>excluded-artifact</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
                </project>
                """);

        assertEquals("1.4.2", pom.properties().get("truth.version"));
        RawPomDependency dependency = pom.dependencies().getFirst();
        assertEquals("provided", dependency.scope().orElseThrow());
        assertTrue(dependency.optional());
        assertEquals("com.example", dependency.exclusions().getFirst().groupId());
        assertEquals("excluded-artifact", dependency.exclusions().getFirst().artifactId());
    }

    @Test
    void parsesNamespacedPomByLocalElementName() {
        RawPom pom = parser.parse("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>namespaced-app</artifactId>
                  <version>1.2.3</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.slf4j</groupId>
                      <artifactId>slf4j-api</artifactId>
                      <version>2.0.16</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        assertEquals("org.example", pom.groupId().orElseThrow());
        assertEquals("namespaced-app", pom.artifactId());
        assertEquals("1.2.3", pom.version().orElseThrow());
        assertEquals("org.slf4j", pom.dependencies().getFirst().groupId());
        assertEquals("slf4j-api", pom.dependencies().getFirst().artifactId());
    }

    @Test
    void parsesDistributionManagementRelocation() {
        RawPom pom = parser.parse("""
                <project>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-junit5</artifactId>
                  <version>3.33.2</version>
                  <distributionManagement>
                    <relocation>
                      <groupId>io.quarkus</groupId>
                      <artifactId>quarkus-junit</artifactId>
                      <version>3.33.2</version>
                      <message>Use quarkus-junit instead.</message>
                    </relocation>
                  </distributionManagement>
                </project>
                """);

        RawPomRelocation relocation = pom.relocation().orElseThrow();
        assertEquals("io.quarkus", relocation.groupId().orElseThrow());
        assertEquals("quarkus-junit", relocation.artifactId().orElseThrow());
        assertEquals("3.33.2", relocation.version().orElseThrow());
        assertEquals("Use quarkus-junit instead.", relocation.message().orElseThrow());
    }

    @Test
    void parsesPartialDistributionManagementRelocation() {
        RawPom pom = parser.parse("""
                <project>
                  <groupId>org.legacy</groupId>
                  <artifactId>old-api</artifactId>
                  <version>1.0.0</version>
                  <distributionManagement>
                    <relocation>
                      <artifactId>new-api</artifactId>
                    </relocation>
                  </distributionManagement>
                </project>
                """);

        RawPomRelocation relocation = pom.relocation().orElseThrow();
        assertFalse(relocation.groupId().isPresent());
        assertEquals("new-api", relocation.artifactId().orElseThrow());
        assertFalse(relocation.version().isPresent());
        assertFalse(relocation.message().isPresent());
    }

    @Test
    void trimsDependencyPropertiesClassifierAndRelocationValues() {
        RawPom pom = parser.parse("""
                <project>
                  <groupId> org.example </groupId>
                  <artifactId> metadata </artifactId>
                  <version> 1.0.0 </version>
                  <properties>
                    <dep.version> 2.1.0 </dep.version>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId> org.example </groupId>
                      <artifactId> helper </artifactId>
                      <version> ${dep.version} </version>
                      <scope> runtime </scope>
                      <type> test-jar </type>
                      <classifier> tests </classifier>
                      <optional> false </optional>
                    </dependency>
                  </dependencies>
                  <distributionManagement>
                    <relocation>
                      <groupId> org.example.new </groupId>
                      <artifactId> metadata-new </artifactId>
                      <version> 2.0.0 </version>
                      <message> Use metadata-new. </message>
                    </relocation>
                  </distributionManagement>
                </project>
                """);

        assertEquals("org.example", pom.groupId().orElseThrow());
        assertEquals("metadata", pom.artifactId());
        assertEquals("1.0.0", pom.version().orElseThrow());
        assertEquals("2.1.0", pom.properties().get("dep.version"));

        RawPomDependency dependency = pom.dependencies().getFirst();
        assertEquals("org.example", dependency.groupId());
        assertEquals("helper", dependency.artifactId());
        assertEquals("${dep.version}", dependency.version().orElseThrow());
        assertEquals("runtime", dependency.scope().orElseThrow());
        assertEquals("test-jar", dependency.type().orElseThrow());
        assertEquals("tests", dependency.classifier().orElseThrow());
        assertFalse(dependency.optional());

        RawPomRelocation relocation = pom.relocation().orElseThrow();
        assertEquals("org.example.new", relocation.groupId().orElseThrow());
        assertEquals("metadata-new", relocation.artifactId().orElseThrow());
        assertEquals("2.0.0", relocation.version().orElseThrow());
        assertEquals("Use metadata-new.", relocation.message().orElseThrow());
    }

    @Test
    void emptyDependencyContainersProduceEmptyLists() {
        RawPom pom = parser.parse("""
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>no-dependencies</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                  </dependencies>
                </project>
                """);

        assertTrue(pom.dependencyManagement().isEmpty());
        assertTrue(pom.dependencies().isEmpty());
    }

    @Test
    void defaultsPackagingToJar() {
        RawPom pom = parser.parse("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.slf4j</groupId>
                  <artifactId>slf4j-api</artifactId>
                  <version>2.0.16</version>
                </project>
                """);

        assertEquals("jar", pom.packaging());
    }

    @Test
    void blankOptionalFieldsAreIgnored() {
        RawPom pom = parser.parse("""
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>blank-optionals</artifactId>
                  <version>1.0.0</version>
                  <packaging>   </packaging>
                  <dependencies>
                    <dependency>
                      <groupId>org.slf4j</groupId>
                      <artifactId>slf4j-api</artifactId>
                      <version>   </version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        assertEquals("jar", pom.packaging());
        assertTrue(pom.dependencies().getFirst().version().isEmpty());
    }

    @Test
    void ignoresBuildPluginsForMvp() throws IOException {
        RawPom pom = parseFixture("guava-33.4.0-jre.pom");

        assertEquals(6, pom.dependencies().size());
        assertTrue(pom.dependencies().stream().noneMatch(dependency ->
                dependency.artifactId().equals("maven-compiler-plugin")));
    }

    @Test
    void parserReturnsRawModel() throws IOException {
        RawPom pom = parseFixture("guava-33.4.0-jre.pom");

        assertInstanceOf(RawPom.class, pom);
        assertFalse(pom.groupId().isPresent());
        assertEquals("1.0.2", pom.dependencies().getFirst().version().orElseThrow());
    }

    @Test
    void parsesByteArrayInput() {
        RawPom pom = parser.parse("""
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>byte-input</artifactId>
                  <version>1.0.0</version>
                </project>
                """.getBytes(StandardCharsets.UTF_8));

        assertEquals("org.example", pom.groupId().orElseThrow());
        assertEquals("byte-input", pom.artifactId());
        assertEquals("1.0.0", pom.version().orElseThrow());
    }

    @Test
    void parsesLicenseNameUrlAndDistribution() {
        RawPom pom = parser.parse("""
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>licensed</artifactId>
                  <version>1.0.0</version>
                  <licenses>
                    <license>
                      <name>Apache License, Version 2.0</name>
                      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
                      <distribution>repo</distribution>
                    </license>
                  </licenses>
                </project>
                """);

        assertEquals(1, pom.licenses().size());
        RawPomLicense license = pom.licenses().getFirst();
        assertEquals("Apache License, Version 2.0", license.name().orElseThrow());
        assertEquals("https://www.apache.org/licenses/LICENSE-2.0.txt", license.url().orElseThrow());
        assertEquals("repo", license.distribution().orElseThrow());
        assertTrue(license.comments().isEmpty());
    }

    @Test
    void parsesDualLicensesInDeclarationOrder() {
        RawPom pom = parser.parse("""
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>dual</artifactId>
                  <version>1.0.0</version>
                  <licenses>
                    <license>
                      <name>EPL 2.0</name>
                      <url>https://www.eclipse.org/legal/epl-2.0/</url>
                    </license>
                    <license>
                      <name>GPL2 w/ CPE</name>
                      <url>https://openjdk.java.net/legal/gplv2+ce.html</url>
                    </license>
                  </licenses>
                </project>
                """);

        assertEquals(2, pom.licenses().size());
        assertEquals("EPL 2.0", pom.licenses().get(0).name().orElseThrow());
        assertEquals("GPL2 w/ CPE", pom.licenses().get(1).name().orElseThrow());
    }

    @Test
    void absentLicensesProduceEmptyList() {
        RawPom pom = parser.parse("""
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>unlicensed</artifactId>
                  <version>1.0.0</version>
                </project>
                """);

        assertTrue(pom.licenses().isEmpty());
    }

    @Test
    void parsesLicenseWithUrlOnly() {
        RawPom pom = parser.parse("""
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>url-only</artifactId>
                  <version>1.0.0</version>
                  <licenses>
                    <license>
                      <url>https://opensource.org/licenses/MIT</url>
                    </license>
                  </licenses>
                </project>
                """);

        assertEquals(1, pom.licenses().size());
        assertTrue(pom.licenses().getFirst().name().isEmpty());
        assertEquals("https://opensource.org/licenses/MIT", pom.licenses().getFirst().url().orElseThrow());
    }

    private RawPom parseFixture(String name) throws IOException {
        try (InputStream inputStream = RawPomParserTest.class.getResourceAsStream("/poms/" + name)) {
            if (inputStream == null) {
                throw new IOException("Missing fixture " + name);
            }
            return parser.parse(inputStream);
        }
    }
}
