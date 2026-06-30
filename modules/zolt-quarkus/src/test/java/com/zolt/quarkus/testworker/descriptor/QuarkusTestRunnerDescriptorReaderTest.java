package com.zolt.quarkus.testworker.descriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.testruntime.TestJvmArguments;
import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.testworker.QuarkusTestRunnerRequest;
import com.zolt.test.TestSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusTestRunnerDescriptorReaderTest {
    @TempDir
    private Path projectDir;

    private final QuarkusTestRunnerDescriptorWriter writer = new QuarkusTestRunnerDescriptorWriter();
    private final QuarkusTestRunnerDescriptorReader reader = new QuarkusTestRunnerDescriptorReader();

    @Test
    void readsDescriptorWrittenByWriter() {
        QuarkusTestRunnerDescriptor written = writer.write(request());

        QuarkusTestRunnerDescriptor read = reader.read(written.descriptorFile());

        assertEquals(written, read);
    }

    @Test
    void rejectsUnsupportedVersion() throws IOException {
        Path descriptor = descriptorPath();
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, "version=2\n");

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> reader.read(descriptor));

        assertTrue(exception.getMessage().contains("Unsupported Quarkus test runner descriptor"));
        assertTrue(exception.getMessage().contains("Run `zolt test` again"));
    }

    @Test
    void rejectsMissingRequiredField() throws IOException {
        Path descriptor = descriptorPath();
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, """
                version=1
                runnerMode=plain-junit
                """);

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> reader.read(descriptor));

        assertTrue(exception.getMessage().contains("Missing required field `testRuntimeClasspathFile`"));
    }

    @Test
    void rejectsInvalidBooleanField() throws IOException {
        Path descriptor = descriptorPath();
        Path classpath = projectDir.resolve("target/quarkus/test-runtime-classpath.txt");
        Files.createDirectories(classpath.getParent());
        Files.writeString(classpath, projectDir.resolve("target/test-classes") + "\n");
        Files.writeString(descriptor, """
                version=1
                runnerMode=plain-junit
                supportsQuarkusTestAnnotations=maybe
                jbossLogManagerPresent=false
                projectDirectory=%s
                mainOutputDirectory=%s
                testOutputDirectory=%s
                serializedApplicationModel=%s
                bootstrapDescriptorFile=%s
                testRuntimeClasspathFile=%s
                """.formatted(
                projectDir,
                projectDir.resolve("target/classes"),
                projectDir.resolve("target/test-classes"),
                projectDir.resolve("target/quarkus/test-application-model.dat"),
                projectDir.resolve("target/quarkus/zolt-bootstrap.properties"),
                classpath));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> reader.read(descriptor));

        assertTrue(exception.getMessage().contains("Field `supportsQuarkusTestAnnotations` must be true or false"));
    }

    @Test
    void rejectsMissingClasspathFile() throws IOException {
        Path descriptor = descriptorPath();
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, """
                version=1
                runnerMode=plain-junit
                supportsQuarkusTestAnnotations=false
                jbossLogManagerPresent=false
                projectDirectory=%s
                mainOutputDirectory=%s
                testOutputDirectory=%s
                serializedApplicationModel=%s
                bootstrapDescriptorFile=%s
                testRuntimeClasspathFile=%s
                """.formatted(
                projectDir,
                projectDir.resolve("target/classes"),
                projectDir.resolve("target/test-classes"),
                projectDir.resolve("target/quarkus/test-application-model.dat"),
                projectDir.resolve("target/quarkus/zolt-bootstrap.properties"),
                projectDir.resolve("target/quarkus/missing-test-runtime-classpath.txt")));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> reader.read(descriptor));

        assertTrue(exception.getMessage().contains("Could not read Quarkus test runtime classpath file"));
        assertTrue(exception.getMessage().contains("referenced by"));
    }

    @Test
    void preservesBackslashesInPathValues() throws IOException {
        Path descriptor = descriptorPath();
        Path classpath = projectDir.resolve("target/quarkus/test-runtime-classpath.txt");
        Files.createDirectories(classpath.getParent());
        Files.writeString(classpath, """
                C:\\repo\\app\\target\\test-classes
                C:\\repo\\app\\target\\classes
                """);
        Files.writeString(descriptor, """
                version=1
                runnerMode=plain-junit
                supportsQuarkusTestAnnotations=false
                jbossLogManagerPresent=true
                projectDirectory=C:\\repo\\app
                mainOutputDirectory=C:\\repo\\app\\target\\classes
                testOutputDirectory=C:\\repo\\app\\target\\test-classes
                serializedApplicationModel=C:\\repo\\app\\target\\quarkus\\test-application-model.dat
                bootstrapDescriptorFile=C:\\repo\\app\\target\\quarkus\\zolt-bootstrap.properties
                testRuntimeClasspathFile=%s
                """.formatted(classpath));

        QuarkusTestRunnerDescriptor read = reader.read(descriptor);

        assertEquals("C:\\repo\\app", read.projectDirectory().toString());
        assertEquals("C:\\repo\\app\\target\\test-classes", read.testRuntimeClasspath().getFirst().toString());
    }

    @Test
    void readsSelectionFields() throws IOException {
        Path descriptor = descriptorPath();
        Path classpath = projectDir.resolve("target/quarkus/test-runtime-classpath.txt");
        Files.createDirectories(classpath.getParent());
        Files.writeString(classpath, projectDir.resolve("target/test-classes") + "\n");
        Files.writeString(descriptor, """
                version=1
                runnerMode=plain-junit
                supportsQuarkusTestAnnotations=false
                jbossLogManagerPresent=false
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
                projectDir,
                projectDir.resolve("target/classes"),
                projectDir.resolve("target/test-classes"),
                projectDir.resolve("target/quarkus/test-application-model.dat"),
                projectDir.resolve("target/quarkus/zolt-bootstrap.properties"),
                classpath));

        QuarkusTestRunnerDescriptor read = reader.read(descriptor);

        assertEquals(
                TestSelection.fromFields(
                        List.of("com.example.MainTest"),
                        List.of(new TestSelection.MethodSelector("com.example.OtherTest", "runs")),
                        List.of("*ServiceTest"),
                        List.of("fast"),
                        List.of("slow")),
                read.testSelection());
    }

    private Path descriptorPath() {
        return projectDir.resolve("target/quarkus/zolt-test-bootstrap.properties");
    }

    private QuarkusTestRunnerRequest request() {
        return new QuarkusTestRunnerRequest(
                projectDir,
                projectDir.resolve("target/classes"),
                projectDir.resolve("target/test-classes"),
                projectDir.resolve("target/quarkus/test-application-model.dat"),
                projectDir.resolve("target/quarkus/zolt-bootstrap.properties"),
                List.of(
                        projectDir.resolve("target/test-classes"),
                        projectDir.resolve("target/classes"),
                        projectDir.resolve(".zolt/cache/org/junit/platform/junit-platform-console-standalone.jar")),
                true,
                TestSelection.empty(),
                new TestJvmArguments(List.of("-Dlibrary.mode=true")));
    }
}
