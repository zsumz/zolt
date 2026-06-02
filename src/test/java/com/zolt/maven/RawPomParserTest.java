package com.zolt.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
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
    void ignoresBuildPluginsForMvp() throws IOException {
        RawPom pom = parseFixture("guava-33.4.0-jre.pom");

        assertEquals(6, pom.dependencies().size());
        assertTrue(pom.dependencies().stream().noneMatch(dependency ->
                dependency.artifactId().equals("maven-compiler-plugin")));
    }

    @Test
    void malformedXmlFailsCleanly() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("<project><artifactId>broken</project>"));

        assertTrue(exception.getMessage().contains("Could not parse POM XML."));
        assertTrue(exception.getMessage().contains("Fix malformed XML"));
    }

    @Test
    void missingRequiredDependencyFieldFailsCleanly() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("""
                        <project>
                          <artifactId>bad</artifactId>
                          <dependencies>
                            <dependency>
                              <groupId>com.example</groupId>
                            </dependency>
                          </dependencies>
                        </project>
                        """));

        assertEquals(
                "Could not parse POM XML. Missing required <artifactId> in <dependency>.",
                exception.getMessage());
    }

    @Test
    void parserReturnsRawModel() throws IOException {
        RawPom pom = parseFixture("guava-33.4.0-jre.pom");

        assertInstanceOf(RawPom.class, pom);
        assertFalse(pom.groupId().isPresent());
        assertEquals("1.0.2", pom.dependencies().getFirst().version().orElseThrow());
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
