package com.zolt.classpath;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import org.junit.jupiter.api.Test;

final class ClasspathLaneAuditFormatterEnterpriseJsonTest {
    private final ClasspathLaneAuditFormatter formatter = new ClasspathLaneAuditFormatter();

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
        assertPackageAuditContains(
                output,
                "com.google.protobuf:protoc:4.28.3",
                "tool-protobuf",
                true,
                "[\"tool-protobuf\"]",
                false,
                "protobuf-generator-tooling-only");
        assertPackageAuditContains(
                output,
                "org.jacoco:org.jacoco.cli:0.8.14",
                "tool-coverage",
                false,
                "[\"tool-coverage\"]",
                false,
                "coverage-tooling-only");
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

                [[package]]
                id = "com.google.protobuf:protoc"
                version = "4.28.3"
                source = "maven-central"
                scope = "tool-protobuf"
                direct = true
                jar = "com/google/protobuf/protoc/4.28.3/protoc-4.28.3.jar"
                dependencies = []

                [[package]]
                id = "org.jacoco:org.jacoco.cli"
                version = "0.8.14"
                source = "maven-central"
                scope = "tool-coverage"
                direct = false
                jar = "org/jacoco/org.jacoco.cli/0.8.14/org.jacoco.cli-0.8.14.jar"
                dependencies = []
                """);
    }
}
