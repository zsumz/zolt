package sh.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
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
    void malformedXmlFailsCleanly() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("<project><artifactId>broken</project>"));

        assertTrue(exception.getMessage().contains("Could not parse POM XML."));
        assertTrue(exception.getMessage().contains("Fix malformed XML"));
    }

    @Test
    void nonProjectRootFailsCleanly() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("<metadata><groupId>com.example</groupId></metadata>"));

        assertEquals(
                "Could not parse POM XML. Expected root <project> element.",
                exception.getMessage());
    }

    @Test
    void doctypeIsRejectedAsMalformedPomMetadata() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("""
                        <!DOCTYPE project [
                          <!ENTITY secret SYSTEM "file:///etc/passwd">
                        ]>
                        <project>
                          <artifactId>&secret;</artifactId>
                        </project>
                        """));

        assertTrue(exception.getMessage().contains("Could not parse POM XML."));
        assertTrue(exception.getMessage().contains("Fix malformed XML"));
    }

    @Test
    void malformedXmlDoesNotWriteParserNoiseToStderr() {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));

            assertThrows(
                    RawPomParseException.class,
                    () -> parser.parse("<project><artifactId>broken</project>"));
        } finally {
            System.setErr(originalErr);
        }

        assertEquals("", captured.toString(StandardCharsets.UTF_8));
    }

    @Test
    void inputStreamReadFailureIsActionable() {
        IOException readFailure = new IOException("test read failure");
        InputStream failingInput = new InputStream() {
            @Override
            public int read() throws IOException {
                throw readFailure;
            }
        };

        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse(failingInput));

        assertEquals("Could not read POM XML input.", exception.getMessage());
        assertEquals(readFailure, exception.getCause());
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
    void missingProjectArtifactIdFailsCleanly() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("""
                        <project>
                          <groupId>com.example</groupId>
                          <version>1.0.0</version>
                        </project>
                        """));

        assertEquals(
                "Could not parse POM XML. Missing required <artifactId> in <project>.",
                exception.getMessage());
    }

    @Test
    void missingRequiredParentFieldFailsCleanly() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("""
                        <project>
                          <parent>
                            <groupId>com.example</groupId>
                            <version>1.0.0</version>
                          </parent>
                          <artifactId>child</artifactId>
                        </project>
                        """));

        assertEquals(
                "Could not parse POM XML. Missing required <artifactId> in <parent>.",
                exception.getMessage());
    }

    @Test
    void missingRequiredExclusionFieldFailsCleanly() {
        RawPomParseException exception = assertThrows(
                RawPomParseException.class,
                () -> parser.parse("""
                        <project>
                          <artifactId>bad</artifactId>
                          <dependencies>
                            <dependency>
                              <groupId>com.example</groupId>
                              <artifactId>dep</artifactId>
                              <exclusions>
                                <exclusion>
                                  <artifactId>excluded</artifactId>
                                </exclusion>
                              </exclusions>
                            </dependency>
                          </dependencies>
                        </project>
                        """));

        assertEquals(
                "Could not parse POM XML. Missing required <groupId> in <exclusion>.",
                exception.getMessage());
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

    private RawPom parseFixture(String name) throws IOException {
        try (InputStream inputStream = RawPomParserTest.class.getResourceAsStream("/poms/" + name)) {
            if (inputStream == null) {
                throw new IOException("Missing fixture " + name);
            }
            return parser.parse(inputStream);
        }
    }
}
