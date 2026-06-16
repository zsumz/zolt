package com.zolt.classpath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import org.junit.jupiter.api.Test;

final class ClasspathLaneAuditFormatterJsonTest {
    private final ClasspathLaneAuditFormatter formatter = new ClasspathLaneAuditFormatter();

    @Test
    void formatsStableJsonLaneAudit() {
        String output = formatter.formatJson(lockfile());

        assertEquals("""
                {
                  "schemaVersion": 1,
                  "command": "classpath audit",
                  "lanes": [
                    {
                      "scope": "compile",
                      "compile": true,
                      "runtime": true,
                      "test": true,
                      "processor": false,
                      "testProcessor": false,
                      "toolOpenapi": false,
                      "toolCoverage": false,
                      "packageDefault": true,
                      "disposition": "package-default"
                    },
                    {
                      "scope": "runtime",
                      "compile": false,
                      "runtime": true,
                      "test": true,
                      "processor": false,
                      "testProcessor": false,
                      "toolOpenapi": false,
                      "toolCoverage": false,
                      "packageDefault": true,
                      "disposition": "package-default"
                    },
                    {
                      "scope": "dev",
                      "compile": false,
                      "runtime": true,
                      "test": true,
                      "processor": false,
                      "testProcessor": false,
                      "toolOpenapi": false,
                      "toolCoverage": false,
                      "packageDefault": false,
                      "disposition": "development-only"
                    },
                    {
                      "scope": "test",
                      "compile": false,
                      "runtime": false,
                      "test": true,
                      "processor": false,
                      "testProcessor": false,
                      "toolOpenapi": false,
                      "toolCoverage": false,
                      "packageDefault": false,
                      "disposition": "test-only"
                    },
                    {
                      "scope": "provided",
                      "compile": true,
                      "runtime": false,
                      "test": false,
                      "processor": false,
                      "testProcessor": false,
                      "toolOpenapi": false,
                      "toolCoverage": false,
                      "packageDefault": false,
                      "disposition": "provided-container"
                    },
                    {
                      "scope": "processor",
                      "compile": false,
                      "runtime": false,
                      "test": false,
                      "processor": true,
                      "testProcessor": false,
                      "toolOpenapi": false,
                      "toolCoverage": false,
                      "packageDefault": false,
                      "disposition": "processor-only"
                    },
                    {
                      "scope": "test-processor",
                      "compile": false,
                      "runtime": false,
                      "test": false,
                      "processor": false,
                      "testProcessor": true,
                      "toolOpenapi": false,
                      "toolCoverage": false,
                      "packageDefault": false,
                      "disposition": "processor-only"
                    },
                    {
                      "scope": "quarkus-deployment",
                      "compile": false,
                      "runtime": false,
                      "test": false,
                      "processor": false,
                      "testProcessor": false,
                      "toolOpenapi": false,
                      "toolCoverage": false,
                      "packageDefault": false,
                      "disposition": "quarkus-augmentation-only"
                    },
                    {
                      "scope": "tool-openapi",
                      "compile": false,
                      "runtime": false,
                      "test": false,
                      "processor": false,
                      "testProcessor": false,
                      "toolOpenapi": true,
                      "toolCoverage": false,
                      "packageDefault": false,
                      "disposition": "openapi-generator-tooling-only"
                    },
                    {
                      "scope": "tool-coverage",
                      "compile": false,
                      "runtime": false,
                      "test": false,
                      "processor": false,
                      "testProcessor": false,
                      "toolOpenapi": false,
                      "toolCoverage": true,
                      "packageDefault": false,
                      "disposition": "coverage-tooling-only"
                    }
                  ],
                  "packages": [
                    {
                      "coordinate": "com.example:compile-lib:1.0.0",
                      "scope": "compile",
                      "direct": true,
                      "lanes": ["compile", "runtime", "test"],
                      "packageDefault": true,
                      "disposition": "package-default"
                    },
                    {
                      "coordinate": "com.example:devtools:1.0.0",
                      "scope": "dev",
                      "direct": true,
                      "lanes": ["runtime", "test"],
                      "packageDefault": false,
                      "disposition": "development-only"
                    },
                    {
                      "coordinate": "jakarta.servlet:jakarta.servlet-api:6.1.0",
                      "scope": "provided",
                      "direct": true,
                      "lanes": ["compile"],
                      "packageDefault": false,
                      "disposition": "provided-container"
                    }
                  ]
                }
                """, output);
    }

    private static ZoltLockfile lockfile() {
        return new ZoltLockfileReader().read("""
                version = 1

                [[package]]
                id = "jakarta.servlet:jakarta.servlet-api"
                version = "6.1.0"
                source = "maven-central"
                scope = "provided"
                direct = true
                jar = "jakarta/servlet/jakarta.servlet-api/6.1.0/jakarta.servlet-api-6.1.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:devtools"
                version = "1.0.0"
                source = "maven-central"
                scope = "dev"
                direct = true
                jar = "com/example/devtools/1.0.0/devtools-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:compile-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "com/example/compile-lib/1.0.0/compile-lib-1.0.0.jar"
                dependencies = []
                """);
    }

}
