package com.zolt.explain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GradleStaticProjectInspectorEnterpriseTest {
    @TempDir
    private Path tempDir;

    private final GradleStaticProjectInspector inspector = new GradleStaticProjectInspector();

    @Test
    void reportsEnterpriseSpringBootGradleSignals() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'enterprise'\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                import org.apache.tools.ant.filters.ReplaceTokens
                import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

                plugins {
                    id 'java'
                    id 'war'
                    id 'maven-publish'
                    id 'jacoco'
                    id 'org.openapi.generator' version '7.11.0'
                    id 'org.springframework.boot' version '3.3.6'
                    id 'io.spring.dependency-management' version '1.1.6'
                }

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(17)
                    }
                }

                tasks.named('bootWar') {
                    exclude('WEB-INF/lib/tomcat-*.jar')
                }

                configurations {
                    all {
                        exclude group: 'commons-logging', module: 'commons-logging'
                        resolutionStrategy {
                            force 'org.apache.tomcat.embed:tomcat-embed-core:10.1.40'
                        }
                    }
                }

                repositories {
                    if (useMavenLocal) {
                        mavenLocal()
                    }
                    maven {
                        url = 'https://artifactory.example.com/artifactory/java-virtual'
                        credentials {
                            username = System.getenv('ARTIFACTORY_USERNAME')
                            password = System.getenv('ARTIFACTORY_ACCESS_TOKEN')
                        }
                    }
                }

                tasks.register('buildJavaRestTemplateSdk', GenerateTask) {
                    generatorName = 'spring'
                    inputSpec = "$projectDir/src/main/resources/api-specification.yaml"
                    outputDir = "${buildDir}/generated/com/example/service"
                }

                sourceSets {
                    main {
                        java {
                            srcDirs += "${buildDir}/generated/com/example/service".toString()
                        }
                    }
                }

                tasks.named('test') {
                    systemProperty 'logs.dir', "${project.rootDir}/test-logs"
                    environment 'TZ', 'America/Chicago'
                    testLogging {
                        events 'passed', 'skipped', 'failed'
                    }
                }

                publishing {
                    publications {
                        mavenJava(MavenPublication) {
                            artifact bootWar
                        }
                    }
                }

                processResources {
                    filter(ReplaceTokens, tokens: [projectVersion: project.property('version') as String])
                }
                """);

        GradleInspectionResult result = inspector.inspect(tempDir);

        assertSignalIds(
                result,
                "gradle.enterprise-plugin.mapped",
                "gradle.repository.credentials",
                "gradle.repository.maven-local",
                "gradle.dependency-policy.mutation",
                "gradle.openapi.generated-sources",
                "gradle.resource-filtering",
                "gradle.test-runtime-settings",
                "gradle.package.archive-mutation",
                "gradle.publication.detected");
        assertTrue(result.signals().stream().anyMatch(signal ->
                signal.id().equals("gradle.enterprise-plugin.mapped")
                        && signal.message().contains("org.springframework.boot")));
        assertTrue(result.signals().stream().anyMatch(signal ->
                signal.id().equals("gradle.package.archive-mutation")
                        && signal.category() == ExplainSignal.Category.MIGRATION_BLOCKER));
    }

    private static void assertSignalIds(GradleInspectionResult result, String... expectedIds) {
        for (String expectedId : expectedIds) {
            assertTrue(
                    result.signals().stream().anyMatch(signal -> signal.id().equals(expectedId)),
                    () -> "missing signal " + expectedId + " in " + result.signals());
        }
    }
}
