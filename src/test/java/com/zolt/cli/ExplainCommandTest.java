package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExplainCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void explainHelpShowsMigrationAuditCommand() {
        CommandResult result = execute("explain", "--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Audit a Maven or Gradle project for future Zolt migration."));
        assertTrue(result.stdout().contains("--blockers"));
        assertTrue(result.stdout().contains("--format"));
        assertTrue(result.stdout().contains("--scorecard"));
        assertTrue(result.stdout().contains("--source"));
    }

    @Test
    void explainTextPlaceholderIsActionableWhenSourceIsUnknown() {
        CommandResult result = execute("explain", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("zolt explain is not implemented yet."));
        assertTrue(result.stdout().contains("audit Maven and Gradle project metadata statically"));
        assertTrue(result.stdout().contains("This command will not execute Maven or Gradle"));
        assertTrue(result.stdout().contains("Requested source: auto"));
        assertTrue(result.stdout().contains("Project root: " + tempDir.toAbsolutePath().normalize()));
        assertTrue(result.stdout().contains("followUps/-add-zolt-explain-command-scaffold.md"));
        assertEquals("", result.stderr());
    }

    @Test
    void explainGradleJsonInspectsBuildStatically() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'demo'\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'com.google.guava:guava:33.4.8-jre' }
                """);

        CommandResult result = execute(
                "explain",
                "--cwd", tempDir.toString(),
                "--source", "gradle",
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().startsWith("{\n  \"schemaVersion\": 1,"));
        assertTrue(result.stdout().contains("\"source\": \"gradle\""));
        assertTrue(result.stdout().contains("\"root\": \"" + jsonPath(tempDir.toAbsolutePath().normalize()) + "\""));
        assertTrue(result.stdout().contains("\"resolvedCoordinate\": \"com.google.guava:guava:33.4.8-jre\""));
        assertEquals("", result.stderr());
    }

    @Test
    void explainMavenTextInspectsPomStatically() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                      <version>33.4.8-jre</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        CommandResult result = execute("explain", "--cwd", tempDir.toString(), "--source", "maven");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Zolt explain: Maven project"));
        assertTrue(result.stdout().contains("Projects: 1"));
        assertTrue(result.stdout().contains("demo, packaging=jar, java=21"));
        assertTrue(result.stdout().contains("dependencies: 1"));
        assertTrue(result.stdout().contains("did not execute Maven"));
        assertEquals("", result.stderr());
    }

    @Test
    void explainMavenJsonInspectsPomDeterministically() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <artifactId>demo</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                      <version>5.11.4</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);

        CommandResult result = execute(
                "explain",
                "--cwd", tempDir.toString(),
                "--source", "maven",
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().startsWith("{\n  \"schemaVersion\": 1,"));
        assertTrue(result.stdout().contains("\"source\": \"maven\""));
        assertTrue(result.stdout().contains("\"root\": \"" + jsonPath(tempDir.toAbsolutePath().normalize()) + "\""));
        assertTrue(result.stdout().contains("\"coordinate\": \"org.junit.jupiter:junit-jupiter:5.11.4\""));
        assertEquals("", result.stderr());
    }

    @Test
    void explainGradleScorecardJsonReportsReadinessConcerns() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'demo'\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'jacoco'
                    id 'org.openapi.generator' version '7.11.0'
                }
                repositories {
                    mavenLocal()
                    mavenCentral()
                }
                configurations.all {
                    resolutionStrategy.force 'com.google.guava:guava:33.4.8-jre'
                }
                tasks.register('generateApi') { }
                sourceSets {
                    main {
                        java {
                            srcDirs += "${buildDir}/generated/api".toString()
                        }
                    }
                }
                dependencies {
                    implementation 'com.google.guava:guava:33.4.8-jre'
                }
                """);

        CommandResult result = execute(
                "explain",
                "--cwd", tempDir.toString(),
                "--source", "gradle",
                "--scorecard",
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"command\": \"explain-scorecard\""));
        assertTrue(result.stdout().contains("\"source\": \"gradle\""));
        assertTrue(result.stdout().contains("\"name\": \"repositories\""));
        assertTrue(result.stdout().contains("\"status\": \"non-deterministic\""));
        assertTrue(result.stdout().contains("\"category\": \"blocked\""));
        assertTrue(result.stdout().contains("\"sourcePattern\": \"mavenLocal() property switch\""));
        assertTrue(result.stdout().contains("\"zoltPrimitive\": \"local repository overlays\""));
        assertTrue(result.stdout().contains("\"followUp\": \"\""));
        assertEquals("", result.stderr());
    }

    @Test
    void explainGradleBlockerJsonReportsZoltNativeFollowUps() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'demo'\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins { id 'java' }
                def token = findProperty('repoToken') ?: System.getenv('REPO_TOKEN') ?: 'dummy'
                repositories {
                    mavenLocal()
                    maven {
                        url = 'https://repo.example.invalid/maven'
                        credentials {
                            username = 'ci'
                            password = token
                        }
                    }
                }
                configurations.all {
                    exclude group: 'commons-logging', module: 'commons-logging'
                }
                tasks.named('jar') {
                    exclude('BOOT-INF/lib/tomcat-*.jar')
                }
                dependencies {
                    implementation 'com.google.guava:guava:33.4.8-jre'
                }
                """);

        CommandResult result = execute(
                "explain",
                "--cwd", tempDir.toString(),
                "--source", "gradle",
                "--blockers",
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"command\": \"explain-blockers\""));
        assertTrue(result.stdout().contains("\"status\": \"blocked\""));
        assertTrue(result.stdout().contains("\"sourcePattern\": \"credentials resolved from Gradle properties, env, or defaults\""));
        assertTrue(result.stdout().contains("\"zoltPrimitive\": \"[repositories] credential identities\""));
        assertTrue(result.stdout().contains("\"followUp\": \"\""));
        assertTrue(result.stdout().contains("\"sourcePattern\": \"configurations.all, excludes, force, or resolutionStrategy\""));
        assertTrue(result.stdout().contains("\"followUp\": \"\""));
        assertFalse(result.stdout().contains("dummy"));
        assertFalse(result.stdout().contains("REPO_TOKEN"));
        assertEquals("", result.stderr());
    }

    @Test
    void explainMavenReportsMalformedPomCleanly() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project><artifactId>broken</project>");

        CommandResult result = execute("explain", "--cwd", tempDir.toString(), "--source", "maven");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not inspect Maven project."));
        assertTrue(result.stderr().contains("Fix malformed POM XML"));
    }

    @Test
    void explainRejectsInvalidFormatClearly() {
        CommandResult result = execute("explain", "--format", "xml");

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("Invalid value for option '--format'"));
        assertTrue(result.stderr().contains("TEXT"));
        assertTrue(result.stderr().contains("JSON"));
    }

    @Test
    void explainRejectsInvalidSourceClearly() {
        CommandResult result = execute("explain", "--source", "ant");

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("Invalid value for option '--source'"));
        assertTrue(result.stderr().contains("AUTO"));
        assertTrue(result.stderr().contains("MAVEN"));
        assertTrue(result.stderr().contains("GRADLE"));
    }

    @Test
    void explainScaffoldDoesNotExecuteMavenOrGradleWrappers() throws IOException {
        Path projectDir = tempDir.resolve("legacy");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("pom.xml"), """
                <project>
                  <artifactId>legacy</artifactId>
                </project>
                """);
        Path marker = projectDir.resolve("executed.txt");
        Path mvnw = projectDir.resolve("mvnw");
        Path gradlew = projectDir.resolve("gradlew");
        Files.writeString(mvnw, "#!/usr/bin/env sh\nprintf mvn > '" + marker + "'\n");
        Files.writeString(gradlew, "#!/usr/bin/env sh\nprintf gradle > '" + marker + "'\n");
        assertTrue(mvnw.toFile().setExecutable(true));
        assertTrue(gradlew.toFile().setExecutable(true));

        CommandResult maven = execute("explain", "--cwd", projectDir.toString(), "--source", "maven");
        CommandResult gradle = execute("explain", "--cwd", projectDir.toString(), "--source", "gradle");

        assertEquals(0, maven.exitCode());
        assertEquals(1, gradle.exitCode());
        assertFalse(Files.exists(marker));
    }

    private static String jsonPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
