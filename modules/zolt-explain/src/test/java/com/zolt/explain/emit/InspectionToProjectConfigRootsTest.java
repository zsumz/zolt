package com.zolt.explain.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.explain.gradle.GradleStaticProjectInspector;
import com.zolt.explain.maven.MavenStaticProjectInspector;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class InspectionToProjectConfigRootsTest {
    @TempDir
    private Path tempDir;

    private final InspectionToProjectConfig mapper = new InspectionToProjectConfig();

    @Test
    void mavenDraftCarriesAuditedBuildRootsIntoBuildSettings() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>custom-roots</artifactId>
                  <version>1.0.0</version>
                  <build>
                    <sourceDirectory>src/java</sourceDirectory>
                    <testSourceDirectory>src/tests</testSourceDirectory>
                    <resources>
                      <resource>
                        <directory>config</directory>
                      </resource>
                    </resources>
                    <testResources>
                      <testResource>
                        <directory>test-config</directory>
                      </testResource>
                    </testResources>
                  </build>
                </project>
                """);

        ProjectConfig config = mapper.fromMaven(new MavenStaticProjectInspector().inspect(tempDir)).config();

        assertEquals("src/java", config.build().source());
        assertEquals("src/tests", config.build().test());
        assertEquals(List.of("src/tests"), config.build().testSources());
        assertEquals(List.of("config"), config.build().resourceRoots());
        assertEquals(List.of("test-config"), config.build().testResourceRoots());
    }

    @Test
    void mavenDraftCarriesUnsupportedKotlinRootsAsAuditedReviewData() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>kotlin-roots</artifactId>
                  <version>1.0.0</version>
                  <build>
                    <sourceDirectory>src/main/kotlin</sourceDirectory>
                    <testSourceDirectory>src/test/kotlin</testSourceDirectory>
                  </build>
                </project>
                """);

        ProjectConfig config = mapper.fromMaven(new MavenStaticProjectInspector().inspect(tempDir)).config();

        assertEquals("src/main/kotlin", config.build().source());
        assertEquals("src/test/kotlin", config.build().test());
        assertEquals(List.of("src/test/kotlin"), config.build().testSources());
    }

    @Test
    void gradleDraftCarriesAuditedSourceRootsIntoBuildSettings() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'custom-roots'\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins { id 'java' }
                group = 'com.example'
                version = '1.0.0'
                sourceSets {
                    main {
                        java {
                            srcDirs = ['src/java']
                        }
                    }
                    test {
                        java {
                            srcDirs = ['src/tests', 'src/fixtures']
                        }
                    }
                }
                """);

        ProjectConfig config = mapper.fromGradle(new GradleStaticProjectInspector().inspect(tempDir)).config();

        assertEquals("src/java", config.build().source());
        assertEquals("src/fixtures", config.build().test());
        assertEquals(List.of("src/fixtures", "src/tests"), config.build().testSources());
    }
}
