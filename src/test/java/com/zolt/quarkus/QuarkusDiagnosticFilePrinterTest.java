package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusDiagnosticFilePrinterTest {
    @TempDir
    private Path tempDir;

    @AfterEach
    void clearProperties() {
        System.clearProperty(QuarkusDiagnosticFilePrinter.TEST_CLASS_BEAN_DIAGNOSTIC_FILE_PROPERTY);
        System.clearProperty(QuarkusDiagnosticFilePrinter.QUARKUS_BUILDER_GRAPH_OUTPUT_PROPERTY);
    }

    @Test
    void skipsDiagnosticsWhenPropertiesAreUnset() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusDiagnosticFilePrinter printer = printer(out);

        printer.writeTestClassBeanCustomizerDiagnostic();
        printer.writeBuildGraphDiagnostic();

        assertTrue(output(out).isEmpty());
    }

    @Test
    void writesMissingTestClassBeanDiagnosticFile() {
        Path diagnosticFile = tempDir.resolve("missing-test-class-bean.txt");
        System.setProperty(
                QuarkusDiagnosticFilePrinter.TEST_CLASS_BEAN_DIAGNOSTIC_FILE_PROPERTY,
                diagnosticFile.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        printer(out).writeTestClassBeanCustomizerDiagnostic();

        String output = output(out);
        assertTrue(output.contains("Zolt Quarkus test-class-bean customizer diagnostic:"));
        assertTrue(output.contains("file=" + diagnosticFile.toAbsolutePath().normalize()));
        assertTrue(output.contains("entries=<missing>"));
    }

    @Test
    void writesTestClassBeanDiagnosticEntries() throws Exception {
        Path diagnosticFile = tempDir.resolve("test-class-bean.txt");
        Files.writeString(diagnosticFile, "testClass=com.example.GreetingResourceTest\nbean=com.example.GreetingResource\n");
        System.setProperty(
                QuarkusDiagnosticFilePrinter.TEST_CLASS_BEAN_DIAGNOSTIC_FILE_PROPERTY,
                diagnosticFile.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        printer(out).writeTestClassBeanCustomizerDiagnostic();

        String output = output(out);
        assertTrue(output.contains("  testClass=com.example.GreetingResourceTest"));
        assertTrue(output.contains("  bean=com.example.GreetingResource"));
    }

    @Test
    void writesBuildGraphSummary() throws Exception {
        Path graphFile = tempDir.resolve("graph.txt");
        Files.writeString(graphFile, """
                ZoltQuarkusTestClassBeanCustomizer
                TestClassBeanBuildItem
                OtherBuildItem
                """);
        System.setProperty(
                QuarkusDiagnosticFilePrinter.QUARKUS_BUILDER_GRAPH_OUTPUT_PROPERTY,
                graphFile.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        printer(out).writeBuildGraphDiagnostic();

        String output = output(out);
        assertTrue(output.contains("Zolt Quarkus build-chain graph diagnostic:"));
        assertTrue(output.contains("containsZoltCustomizer=true"));
        assertTrue(output.contains("containsTestClassBeanBuildItem=true"));
        assertTrue(output.contains("containsAdditionalBeanBuildItem=false"));
        assertTrue(output.contains("lines=3"));
    }

    @Test
    void writesMissingBuildGraphFile() {
        Path graphFile = tempDir.resolve("missing-graph.txt");
        System.setProperty(
                QuarkusDiagnosticFilePrinter.QUARKUS_BUILDER_GRAPH_OUTPUT_PROPERTY,
                graphFile.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        printer(out).writeBuildGraphDiagnostic();

        String output = output(out);
        assertTrue(output.contains("Zolt Quarkus build-chain graph diagnostic:"));
        assertTrue(output.contains("file=" + graphFile.toAbsolutePath().normalize()));
        assertTrue(output.contains("entries=<missing>"));
        assertFalse(output.contains("containsZoltCustomizer"));
    }

    private static QuarkusDiagnosticFilePrinter printer(ByteArrayOutputStream out) {
        return new QuarkusDiagnosticFilePrinter(new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    private static String output(ByteArrayOutputStream out) {
        return out.toString(StandardCharsets.UTF_8);
    }
}
