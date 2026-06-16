package com.zolt.explain;

final class MigrationExplainGoldenFixtures {
    private MigrationExplainGoldenFixtures() {
    }

    static String mavenSimpleText() {
        return """
                Zolt explain: Maven project

                Project
                  Root: $ROOT
                  Projects: 1
                  Signals: 0

                Projects
                  - . (maven-simple, packaging=jar, java=21)
                    dependencies: 2
                    managed dependencies: 0
                    imported BOMs: 0
                    plugins: 0
                    profiles: 0

                What Zolt can build
                  ok    no static buildability issues found in this first inspection pass

                What can cache
                  ok    no static cacheability issues found in this first inspection pass

                Non-determinism
                  ok    no static non-determinism issues found in this first inspection pass

                Migration blockers
                  ok    no static migration-blocker issues found in this first inspection pass

                Next steps
                  1. Review the static report, then create zolt.toml and run zolt resolve.

                This command inspected Maven metadata statically and did not execute Maven.
                """;
    }

    static String mavenSimpleJson() {
        return """
                {
                  "schemaVersion": 1,
                  "source": "maven",
                  "root": "$ROOT",
                  "summary": {
                    "projects": 1,
                    "signals": 0,
                    "blockers": 0,
                    "warnings": 0,
                    "unknown": 0,
                    "ok": 1
                  },
                  "projects": [
                    {
                      "path": ".",
                      "name": "maven-simple",
                      "packaging": "jar",
                      "javaVersion": "21",
                      "modules": [],
                      "sourceRoots": ["src/main/java"],
                      "testSourceRoots": ["src/test/java"],
                      "resourceRoots": ["src/main/resources"],
                      "dependencies": [
                        {
                          "scope": "compile",
                          "coordinate": "com.google.guava:guava:33.4.8-jre",
                          "version": "33.4.8-jre",
                          "type": "jar",
                          "optional": false,
                          "managed": false,
                          "importedBom": false
                        },
                        {
                          "scope": "test",
                          "coordinate": "org.junit.jupiter:junit-jupiter:5.11.4",
                          "version": "5.11.4",
                          "type": "jar",
                          "optional": false,
                          "managed": false,
                          "importedBom": false
                        }
                      ],
                      "dependencyManagement": [],
                      "importedBoms": [],
                      "plugins": [],
                      "profiles": []
                    }
                  ],
                  "signals": [],
                  "migration": {
                    "status": "ready",
                    "nextSteps": ["Review the static report, then create zolt.toml and run zolt resolve."]
                  }
                }
                """;
    }

    static String gradleSimpleText() {
        return """
                Zolt explain: Gradle project

                Project
                  Root: $ROOT
                  Settings: settings.gradle
                  Included projects: 0
                  Projects: 1
                  Version catalog aliases: 0
                  Signals: 0

                Projects
                  - . (gradle-simple, dsl=groovy, java=21)
                    build file: build.gradle
                    plugins: 2
                    repositories: 1
                    dependencies: 2

                What Zolt can build
                  ok    no static buildability issues found in this first inspection pass

                What can cache
                  ok    no static cacheability issues found in this first inspection pass

                Non-determinism
                  ok    no static non-determinism issues found in this first inspection pass

                Migration blockers
                  ok    no static migration-blocker issues found in this first inspection pass

                Next steps
                  1. Review the static report, then create zolt.toml and run zolt resolve.

                This command inspected Gradle metadata statically and did not execute Gradle.
                """;
    }

    static String gradleSimpleJson() {
        return """
                {
                  "schemaVersion": 1,
                  "source": "gradle",
                  "root": "$ROOT",
                  "settingsFile": "settings.gradle",
                  "summary": {
                    "includedProjects": 0,
                    "projects": 1,
                    "versionCatalogAliases": 0,
                    "signals": 0,
                    "blockers": 0,
                    "warnings": 0,
                    "unknown": 0,
                    "ok": 1
                  },
                  "includedProjects": [],
                  "versionCatalogAliases": [],
                  "projects": [
                    {
                      "path": ".",
                      "name": "gradle-simple",
                      "buildFile": "build.gradle",
                      "dsl": "groovy",
                      "javaVersion": "21",
                      "plugins": [
                        {
                          "id": "application",
                          "version": ""
                        },
                        {
                          "id": "java",
                          "version": ""
                        }
                      ],
                      "repositories": [
                        {
                          "kind": "mavenCentral",
                          "url": "https://repo.maven.apache.org/maven2"
                        }
                      ],
                      "dependencies": [
                        {
                          "configuration": "implementation",
                          "notation": "com.google.guava:guava:33.4.8-jre",
                          "resolvedCoordinate": "com.google.guava:guava:33.4.8-jre",
                          "versionCatalogAlias": ""
                        },
                        {
                          "configuration": "testImplementation",
                          "notation": "org.junit.jupiter:junit-jupiter:5.11.4",
                          "resolvedCoordinate": "org.junit.jupiter:junit-jupiter:5.11.4",
                          "versionCatalogAlias": ""
                        }
                      ],
                      "sourceRoots": ["src/main/java"],
                      "testSourceRoots": ["src/test/java"]
                    }
                  ],
                  "signals": [],
                  "migration": {
                    "status": "ready",
                    "nextSteps": ["Review the static report, then create zolt.toml and run zolt resolve."]
                  }
                }
                """;
    }

    static String gradleEnterpriseSpringText() {
        return """
                Zolt explain: Gradle project

                Project
                  Root: $ROOT
                  Settings: settings.gradle
                  Included projects: 0
                  Projects: 1
                  Version catalog aliases: 0
                  Signals: 17

                Projects
                  - . (gradle-enterprise-spring, dsl=groovy, java=17)
                    build file: build.gradle
                    plugins: 7
                    repositories: 2
                    dependencies: 6

                What Zolt can build
                  warn  Gradle build declares custom tasks.
                  warn  Gradle OpenAPI generator tasks feed generated Java sources into sourceSets.
                  warn  Gradle Maven Publish configuration selects artifacts and repositories.
                  warn  Gradle test task declares runtime properties, environment, JVM args, or event logging.
                  ok  Gradle plugin `io.spring.dependency-management` maps to Zolt [platforms] BOM imports and dependency policy.
                  ok  Gradle plugin `jacoco` maps to Zolt coverage command.
                  ok  Gradle plugin `java` maps to Zolt Java source, javac, classpath, and package primitives.
                  ok  Gradle plugin `maven-publish` maps to planned Zolt publication metadata, dry-run, and publish commands.
                  ok  Gradle plugin `org.openapi.generator` maps to Zolt typed OpenAPI generated-source steps.
                  ok  Gradle plugin `org.springframework.boot` maps to Zolt Spring Boot platform, run, test, and executable archive support.
                  ok  Gradle plugin `war` maps to Zolt WAR and Spring Boot WAR package modes.

                What can cache
                  warn  Gradle processResources performs token/resource filtering.

                Non-determinism
                  warn  Gradle repository credentials are resolved inside the build script.
                  warn  Gradle build can read Maven-local artifacts through mavenLocal().

                Migration blockers
                  block  Gradle build mutates dependency policy through excludes, resolutionStrategy, or forced versions.
                  block  Gradle build uses imperative dependency or configuration mutation.
                  block  Gradle bootWar package content is changed with archive excludes.

                Next steps
                  1. Replace global Gradle excludes and forced versions with explicit Zolt dependency policy constraints.
                  2. Replace imperative Gradle logic with explicit Zolt dependencies, platforms, processors, and source roots.
                  3. Replace package archive mutation with Zolt dependency scopes and package placement diagnostics.

                This command inspected Gradle metadata statically and did not execute Gradle.
                """;
    }
}
