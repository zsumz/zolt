package com.zolt.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.dependency.PackageId;
import org.junit.jupiter.api.Test;

final class DependencyWhyJsonFormatterTest extends DependencyWhyTestSupport {
    private final DependencyJsonFormatter jsonFormatter = new DependencyJsonFormatter();

    @Test
    void formatsStableJsonForPresentPackage() {
        String output = jsonFormatter.why(config(), lockfile(), new PackageId("com.example", "lib"));

        assertEquals("""
                {
                  "schemaVersion": 1,
                  "command": "why",
                  "project": {
                    "group": "com.example",
                    "name": "demo",
                    "version": "0.1.0",
                    "coordinate": "com.example:demo:0.1.0"
                  },
                  "target": "com.example:lib",
                  "status": "present",
                  "path": [
                    {
                      "id": "com.example:app",
                      "version": "1.0.0",
                      "coordinate": "com.example:app:1.0.0",
                      "scope": "compile",
                      "direct": true,
                      "dependencies": ["com.example:lib:1.0.0"],
                      "policies": []
                    },
                    {
                      "id": "com.example:lib",
                      "version": "1.0.0",
                      "coordinate": "com.example:lib:1.0.0",
                      "scope": "compile",
                      "direct": false,
                      "dependencies": [],
                      "policies": []
                    }
                  ],
                  "policyEffects": []
                }
                """, output);
    }

    @Test
    void formatsStableJsonForExcludedPackage() {
        String output = jsonFormatter.why(config(), excludedLockfile(), new PackageId("commons-logging", "commons-logging"));

        assertEquals("""
                {
                  "schemaVersion": 1,
                  "command": "why",
                  "project": {
                    "group": "com.example",
                    "name": "demo",
                    "version": "0.1.0",
                    "coordinate": "com.example:demo:0.1.0"
                  },
                  "target": "commons-logging:commons-logging",
                  "status": "excluded",
                  "path": [],
                  "policyEffects": [
                    {
                      "kind": "global-exclusion",
                      "id": "commons-logging:commons-logging",
                      "requested": "1.2",
                      "source": "com.example:app:1.0.0",
                      "policy": "[dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)"
                    }
                  ]
                }
                """, output);
    }
}
