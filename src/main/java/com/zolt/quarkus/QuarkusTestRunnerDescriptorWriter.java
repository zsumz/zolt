package com.zolt.quarkus;

import com.zolt.test.TestSelectionCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class QuarkusTestRunnerDescriptorWriter {
    public QuarkusTestRunnerDescriptor write(QuarkusTestRunnerRequest request) {
        if (request == null) {
            throw new QuarkusAugmentationException("Quarkus test runner request is required.");
        }

        Path quarkusDirectory = request.projectDirectory().resolve("target/quarkus");
        Path descriptorFile = quarkusDirectory.resolve("zolt-test-bootstrap.properties");
        Path testRuntimeClasspathFile = quarkusDirectory.resolve("test-runtime-classpath.txt");
        try {
            Files.createDirectories(quarkusDirectory);
            writeClasspath(testRuntimeClasspathFile, request);
            Files.writeString(
                    descriptorFile,
                    descriptor(request, testRuntimeClasspathFile),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not write Quarkus test runner descriptor under "
                            + quarkusDirectory
                            + ". Check that target/ is writable and try again.",
                    exception);
        }

        return new QuarkusTestRunnerDescriptor(
                descriptorFile,
                testRuntimeClasspathFile,
                request.projectDirectory(),
                request.mainOutputDirectory(),
                request.testOutputDirectory(),
                request.serializedApplicationModel(),
                request.bootstrapDescriptorFile(),
                QuarkusTestRunnerRequest.RUNNER_MODE,
                QuarkusTestRunnerRequest.SUPPORTS_QUARKUS_TEST_ANNOTATIONS,
                request.jbossLogManagerPresent(),
                request.testRuntimeClasspath(),
                request.testSelection(),
                request.jvmArguments(),
                request.environment());
    }

    private static String descriptor(
            QuarkusTestRunnerRequest request,
            Path testRuntimeClasspathFile) {
        return """
                version=1
                runnerMode=%s
                supportsQuarkusTestAnnotations=%s
                jbossLogManagerPresent=%s
                projectDirectory=%s
                mainOutputDirectory=%s
                testOutputDirectory=%s
                serializedApplicationModel=%s
                bootstrapDescriptorFile=%s
                testRuntimeClasspathFile=%s
                testSelection.classSelectors=%s
                testSelection.methodSelectors=%s
                testSelection.classNamePatterns=%s
                testSelection.includedTags=%s
                testSelection.excludedTags=%s
                jvmArguments=%s
                environment=%s
                """.formatted(
                QuarkusTestRunnerRequest.RUNNER_MODE,
                QuarkusTestRunnerRequest.SUPPORTS_QUARKUS_TEST_ANNOTATIONS,
                request.jbossLogManagerPresent(),
                request.projectDirectory(),
                request.mainOutputDirectory(),
                request.testOutputDirectory(),
                request.serializedApplicationModel(),
                request.bootstrapDescriptorFile(),
                testRuntimeClasspathFile,
                TestSelectionCodec.encodeStrings(request.testSelection().classSelectors()),
                TestSelectionCodec.encodeMethods(request.testSelection().methodSelectors()),
                TestSelectionCodec.encodeStrings(request.testSelection().classNamePatterns()),
                TestSelectionCodec.encodeStrings(request.testSelection().includedTags()),
                TestSelectionCodec.encodeStrings(request.testSelection().excludedTags()),
                TestSelectionCodec.encodeStrings(request.jvmArguments().values()),
                encodeMap(request.environment()));
    }

    private static String encodeMap(Map<String, String> values) {
        List<String> encoded = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            encoded.add(entry.getKey() + "=" + entry.getValue());
        }
        return TestSelectionCodec.encodeStrings(encoded);
    }

    private static void writeClasspath(
            Path path,
            QuarkusTestRunnerRequest request) throws IOException {
        StringBuilder output = new StringBuilder();
        for (Path entry : request.testRuntimeClasspath()) {
            output.append(entry).append('\n');
        }
        Files.writeString(path, output.toString(), StandardCharsets.UTF_8);
    }
}
