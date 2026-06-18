package com.zolt.classpath;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import org.junit.jupiter.api.Test;

final class ClasspathLaneAuditFormatterJsonTest {
    private final ClasspathLaneAuditFormatter formatter = new ClasspathLaneAuditFormatter();

    @Test
    void formatsStableJsonLaneAudit() {
        String output = formatter.formatJson(lockfile());

        assertTrue(output.contains("\"schemaVersion\": 1"));
        assertTrue(output.contains("\"scope\": \"tool-openapi\""));
        assertTrue(output.contains("\"toolOpenapi\": true"));
        assertTrue(output.contains("\"disposition\": \"openapi-generator-tooling-only\""));
        assertTrue(output.contains("\"scope\": \"tool-protobuf\""));
        assertTrue(output.contains("\"toolProtobuf\": true"));
        assertTrue(output.contains("\"disposition\": \"protobuf-generator-tooling-only\""));
        assertTrue(output.contains("\"scope\": \"tool-coverage\""));
        assertTrue(output.contains("\"toolCoverage\": true"));
        assertTrue(output.contains("\"coordinate\": \"com.example:compile-lib:1.0.0\""));
        assertTrue(output.contains("\"lanes\": [\"compile\", \"runtime\", \"test\"]"));
        assertTrue(output.contains("\"coordinate\": \"jakarta.servlet:jakarta.servlet-api:6.1.0\""));
        assertTrue(output.contains("\"lanes\": [\"compile\"]"));
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
