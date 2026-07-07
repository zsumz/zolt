package sh.zolt.explain;

final class MigrationExplainGradleEnterpriseFixtures {
    private MigrationExplainGradleEnterpriseFixtures() {
    }

    static String text() {
        return """
                Zolt explain: Gradle project

                Project
                  Root: $ROOT
                  Settings: settings.gradle
                  Included projects: 0
                  Projects: 1
                  Version catalog aliases: 0
                  Signals: 18

                Projects
                  - . (enterprise-spring, dsl=groovy, java=17)
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
                  block  Gradle build reads environment variables in executable build logic.
                  warn  Gradle repository credentials are resolved inside the build script.
                  warn  Gradle build can read Maven-local artifacts through mavenLocal().

                Migration blockers
                  block  Gradle build mutates dependency policy through excludes, resolutionStrategy, or forced versions.
                  block  Gradle build uses imperative dependency or configuration mutation.
                  block  Gradle bootWar package content is changed with archive excludes.

                Next steps
                  1. Move environment-selected build behavior into explicit Zolt configuration or local/CI command settings.
                  2. Replace global Gradle excludes and forced versions with explicit Zolt dependency policy constraints.
                  3. Replace imperative Gradle logic with explicit Zolt dependencies, platforms, processors, and source roots.
                  4. Replace package archive mutation with Zolt dependency scopes and package placement diagnostics.

                This command inspected Gradle metadata statically and did not execute Gradle.
                """;
    }

    static String json() {
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
                    "signals": 17,
                    "blockers": 3,
                    "warnings": 5,
                    "unknown": 0,
                    "ok": 11
                  },
                  "includedProjects": [],
                  "versionCatalogAliases": [],
                  "projects": [
                    {
                      "path": ".",
                      "name": "enterprise-spring",
                      "buildFile": "build.gradle",
                      "dsl": "groovy",
                      "javaVersion": "17",
                      "plugins": [
                        {
                          "id": "io.spring.dependency-management",
                          "version": "1.1.7"
                        },
                        {
                          "id": "jacoco",
                          "version": ""
                        },
                        {
                          "id": "java",
                          "version": ""
                        },
                        {
                          "id": "maven-publish",
                          "version": ""
                        },
                        {
                          "id": "org.openapi.generator",
                          "version": "7.11.0"
                        },
                        {
                          "id": "org.springframework.boot",
                          "version": "3.4.4"
                        },
                        {
                          "id": "war",
                          "version": ""
                        }
                      ],
                      "repositories": [
                        {
                          "kind": "mavenCentral",
                          "url": "https://repo.maven.apache.org/maven2"
                        },
                        {
                          "kind": "maven",
                          "url": "https://repo.spring.io/release"
                        }
                      ],
                      "dependencies": [
                        {
                          "configuration": "implementation",
                          "notation": "org.springframework.boot:spring-boot-starter-web:3.4.4",
                          "resolvedCoordinate": "org.springframework.boot:spring-boot-starter-web:3.4.4",
                          "versionCatalogAlias": ""
                        },
                        {
                          "configuration": "implementation",
                          "notation": "com.google.guava:guava:33.4.8-jre",
                          "resolvedCoordinate": "com.google.guava:guava:33.4.8-jre",
                          "versionCatalogAlias": ""
                        },
                        {
                          "configuration": "implementation",
                          "notation": "org.springframework:spring-core:6.2.4",
                          "resolvedCoordinate": "org.springframework:spring-core:6.2.4",
                          "versionCatalogAlias": ""
                        },
                        {
                          "configuration": "testImplementation",
                          "notation": "org.junit.jupiter:junit-jupiter:5.11.4",
                          "resolvedCoordinate": "org.junit.jupiter:junit-jupiter:5.11.4",
                          "versionCatalogAlias": ""
                        },
                        {
                          "configuration": "testImplementation",
                          "notation": "org.mockito:mockito-core:5.14.2",
                          "resolvedCoordinate": "org.mockito:mockito-core:5.14.2",
                          "versionCatalogAlias": ""
                        },
                        {
                          "configuration": "testImplementation",
                          "notation": "org.testcontainers:junit-jupiter:1.20.4",
                          "resolvedCoordinate": "org.testcontainers:junit-jupiter:1.20.4",
                          "versionCatalogAlias": ""
                        }
                      ],
                      "sourceRoots": [
                        "src/main/java",
                        "src/main/openapi/generated"
                      ],
                      "testSourceRoots": [
                        "src/test/java"
                      ],
                      "groovyTestSourceRoots": []
                    }
                  ],
                  "signals": [
                    "custom-tasks",
                    "generated-sources",
                    "archive-mutation",
                    "runtime-properties",
                    "env-variables",
                    "jvm-args",
                    "event-logging",
                    "dependency-policy",
                    "forced-versions",
                    "imperative-dependency-mutation",
                    "boot-war-archive-excludes",
                    "repository-credentials",
                    "maven-local"
                  ],
                  "migration": {
                    "status": "warnings",
                    "nextSteps": [
                      "Replace global Gradle excludes and forced versions with explicit Zolt dependency policy constraints.",
                      "Replace imperative Gradle logic with explicit Zolt dependencies, platforms, processors, and source roots.",
                      "Replace package archive mutation with Zolt dependency scopes and package placement diagnostics."
                    ]
                  }
                }
                """;
    }
}
