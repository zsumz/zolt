package com.zolt.explain;

final class MigrationExplainMavenFixtures {
    private MigrationExplainMavenFixtures() {
    }

    static String simpleText() {
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

    static String simpleJson() {
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
}
