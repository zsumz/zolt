package com.zolt.classpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import org.junit.jupiter.api.Test;

final class ClasspathLaneAuditFormatterTest {
    private final ClasspathLaneAuditFormatter formatter = new ClasspathLaneAuditFormatter();

    @Test
    void formatsTextLanePolicyAndResolvedPackages() {
        String output = formatter.formatText(lockfile());

        assertTrue(output.contains("Classpath lane audit\n\nLane policy:\n"));
        assertTrue(output.contains("compile             yes     yes     yes  no        no             no           yes             package-default"));
        assertTrue(output.contains("provided            yes     no      no   no        no             no           no              provided-container"));
        assertTrue(output.contains("dev                 no      yes     yes  no        no             no           no              development-only"));
        assertTrue(output.contains("processor           no      no      no   yes       no             no           no              processor-only"));
        assertTrue(output.contains("tool-openapi        no      no      no   no        no             yes          no              openapi-generator-tooling-only"));
        assertTrue(output.contains("- com.example:compile-lib:1.0.0 [compile] lanes=compile,runtime,test package=package-default"));
        assertTrue(output.contains("- com.example:devtools:1.0.0 [dev] lanes=runtime,test package=development-only"));
        assertTrue(output.contains("- jakarta.servlet:jakarta.servlet-api:6.1.0 [provided] lanes=compile package=provided-container"));
    }

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
                      "packageDefault": false,
                      "disposition": "openapi-generator-tooling-only"
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

    @Test
    void formatsResolvedEnterpriseLanesForProcessorsAndToolingScopes() {
        String output = formatter.formatJson(enterpriseLaneLockfile());

        assertPackageAuditContains(
                output,
                "com.example:compile-lib:1.0.0",
                "compile",
                true,
                "[\"compile\", \"runtime\", \"test\"]",
                true,
                "package-default");
        assertPackageAuditContains(
                output,
                "com.example:devtools:1.0.0",
                "dev",
                true,
                "[\"runtime\", \"test\"]",
                false,
                "development-only");
        assertPackageAuditContains(
                output,
                "com.example:processor:1.0.0",
                "processor",
                true,
                "[\"processor\"]",
                false,
                "processor-only");
        assertPackageAuditContains(
                output,
                "com.example:test-processor:1.0.0",
                "test-processor",
                true,
                "[\"test-processor\"]",
                false,
                "processor-only");
        assertPackageAuditContains(
                output,
                "io.quarkus:quarkus-rest-deployment:3.33.2",
                "quarkus-deployment",
                false,
                "[\"quarkus-deployment\"]",
                false,
                "quarkus-augmentation-only");
        assertPackageAuditContains(
                output,
                "org.openapitools:openapi-generator-cli:7.11.0",
                "tool-openapi",
                true,
                "[\"tool-openapi\"]",
                false,
                "openapi-generator-tooling-only");
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

    private static void assertPackageAuditContains(
            String output,
            String coordinate,
            String scope,
            boolean direct,
            String lanes,
            boolean packageDefault,
            String disposition) {
        int coordinateIndex = output.indexOf("\"coordinate\": \"" + coordinate + "\"");
        assertTrue(coordinateIndex >= 0, () -> "missing coordinate " + coordinate + " in " + output);
        int objectEnd = output.indexOf('}', coordinateIndex);
        assertTrue(objectEnd >= 0, () -> "missing package object end for " + coordinate);
        String packageObject = output.substring(coordinateIndex, objectEnd);
        assertTrue(packageObject.contains("\"scope\": \"" + scope + "\""));
        assertTrue(packageObject.contains("\"direct\": " + direct));
        assertTrue(packageObject.contains("\"lanes\": " + lanes));
        assertTrue(packageObject.contains("\"packageDefault\": " + packageDefault));
        assertTrue(packageObject.contains("\"disposition\": \"" + disposition + "\""));
    }

    private static ZoltLockfile enterpriseLaneLockfile() {
        return new ZoltLockfileReader().read("""
                version = 1

                [[package]]
                id = "com.example:compile-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "com/example/compile-lib/1.0.0/compile-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = true
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                dependencies = []

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
                id = "org.junit.jupiter:junit-jupiter"
                version = "5.11.4"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/junit/jupiter/junit-jupiter/5.11.4/junit-jupiter-5.11.4.jar"
                dependencies = []

                [[package]]
                id = "com.example:processor"
                version = "1.0.0"
                source = "maven-central"
                scope = "processor"
                direct = true
                jar = "com/example/processor/1.0.0/processor-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:test-processor"
                version = "1.0.0"
                source = "maven-central"
                scope = "test-processor"
                direct = true
                jar = "com/example/test-processor/1.0.0/test-processor-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "io.quarkus:quarkus-rest-deployment"
                version = "3.33.2"
                source = "maven-central"
                scope = "quarkus-deployment"
                direct = false
                jar = "io/quarkus/quarkus-rest-deployment/3.33.2/quarkus-rest-deployment-3.33.2.jar"
                dependencies = []

                [[package]]
                id = "org.openapitools:openapi-generator-cli"
                version = "7.11.0"
                source = "maven-central"
                scope = "tool-openapi"
                direct = true
                jar = "org/openapitools/openapi-generator-cli/7.11.0/openapi-generator-cli-7.11.0.jar"
                dependencies = []
                """);
    }
}
