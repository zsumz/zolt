package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.TestSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusTestRunnerDescriptorWriterTest {
    @TempDir
    private Path projectDir;

    @Test
    void writesDeterministicTestRunnerDescriptor() throws IOException {
        Path junitConsole = projectDir.resolve("cache/junit-platform-console-standalone.jar");
        Path jbossLogManager = projectDir.resolve("cache/jboss-logmanager-3.1.2.Final.jar");
        QuarkusTestRunnerRequest request = new QuarkusTestRunnerRequest(
                projectDir,
                projectDir.resolve("target/classes"),
                projectDir.resolve("target/test-classes"),
                projectDir.resolve("target/quarkus/test-application-model.dat"),
                projectDir.resolve("target/quarkus/zolt-bootstrap.properties"),
                List.of(projectDir.resolve("target/test-classes"), projectDir.resolve("target/classes"), junitConsole, jbossLogManager),
                true,
                TestSelection.fromFields(
                        List.of("com.example.MainTest"),
                        List.of(new TestSelection.MethodSelector("com.example.OtherTest", "runs")),
                        List.of("*ServiceTest"),
                        List.of("fast"),
                        List.of("slow")));

        QuarkusTestRunnerDescriptor descriptor = new QuarkusTestRunnerDescriptorWriter().write(request);

        Path root = projectDir.toAbsolutePath().normalize();
        assertEquals(root.resolve("target/quarkus/zolt-test-bootstrap.properties"), descriptor.descriptorFile());
        assertEquals(root.resolve("target/quarkus/test-runtime-classpath.txt"), descriptor.testRuntimeClasspathFile());
        assertEquals(QuarkusTestRunnerRequest.RUNNER_MODE, descriptor.runnerMode());
        assertFalse(descriptor.supportsQuarkusTestAnnotations());
        assertTrue(descriptor.jbossLogManagerPresent());
        assertEquals("""
                version=1
                runnerMode=plain-junit
                supportsQuarkusTestAnnotations=false
                jbossLogManagerPresent=true
                projectDirectory=%s
                mainOutputDirectory=%s
                testOutputDirectory=%s
                serializedApplicationModel=%s
                bootstrapDescriptorFile=%s
                testRuntimeClasspathFile=%s
                testSelection.classSelectors=com.example.MainTest
                testSelection.methodSelectors=com.example.OtherTest#runs
                testSelection.classNamePatterns=*ServiceTest
                testSelection.includedTags=fast
                testSelection.excludedTags=slow
                """.formatted(
                root,
                root.resolve("target/classes"),
                root.resolve("target/test-classes"),
                root.resolve("target/quarkus/test-application-model.dat"),
                root.resolve("target/quarkus/zolt-bootstrap.properties"),
                root.resolve("target/quarkus/test-runtime-classpath.txt")),
                Files.readString(descriptor.descriptorFile()));
        assertEquals("""
                %s
                %s
                %s
                %s
                """.formatted(
                root.resolve("target/test-classes"),
                root.resolve("target/classes"),
                junitConsole.toAbsolutePath().normalize(),
                jbossLogManager.toAbsolutePath().normalize()),
                Files.readString(descriptor.testRuntimeClasspathFile()));
    }
}
