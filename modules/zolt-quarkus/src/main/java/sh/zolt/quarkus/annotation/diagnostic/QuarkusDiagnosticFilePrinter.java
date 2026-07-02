package sh.zolt.quarkus.annotation.diagnostic;

import java.io.PrintStream;
import java.nio.file.Path;

final class QuarkusDiagnosticFilePrinter {
    static final String TEST_CLASS_BEAN_DIAGNOSTIC_FILE_PROPERTY =
            "zolt.quarkus.test-class-bean-diagnostic-file";
    static final String QUARKUS_BUILDER_GRAPH_OUTPUT_PROPERTY =
            "quarkus.builder.graph-output";

    private final PrintStream out;

    QuarkusDiagnosticFilePrinter(PrintStream out) {
        this.out = out;
    }

    void writeTestClassBeanCustomizerDiagnostic() {
        String diagnosticFile = System.getProperty(TEST_CLASS_BEAN_DIAGNOSTIC_FILE_PROPERTY, "");
        if (diagnosticFile.isBlank()) {
            return;
        }
        Path path = Path.of(diagnosticFile).toAbsolutePath().normalize();
        out.println("Zolt Quarkus test-class-bean customizer diagnostic:");
        out.println("  file=" + path);
        try {
            if (!java.nio.file.Files.isRegularFile(path)) {
                out.println("  entries=<missing>");
                return;
            }
            for (String line : java.nio.file.Files.readAllLines(path)) {
                out.println("  " + line);
            }
        } catch (java.io.IOException | RuntimeException exception) {
            out.println("  entries=<unavailable: " + exception.getClass().getSimpleName() + ">");
        }
    }

    void writeBuildGraphDiagnostic() {
        String graphOutput = System.getProperty(QUARKUS_BUILDER_GRAPH_OUTPUT_PROPERTY, "");
        if (graphOutput.isBlank()) {
            return;
        }
        Path path = Path.of(graphOutput).toAbsolutePath().normalize();
        out.println("Zolt Quarkus build-chain graph diagnostic:");
        out.println("  file=" + path);
        try {
            if (!java.nio.file.Files.isRegularFile(path)) {
                out.println("  entries=<missing>");
                return;
            }
            String graph = java.nio.file.Files.readString(path);
            out.println("  containsZoltCustomizer=" + graph.contains("ZoltQuarkusTestClassBeanCustomizer"));
            out.println("  containsTestClassBeanBuildItem=" + graph.contains("TestClassBeanBuildItem"));
            out.println("  containsAdditionalBeanBuildItem=" + graph.contains("AdditionalBeanBuildItem"));
            out.println("  containsTestsAsBeansProcessor=" + graph.contains("TestsAsBeansProcessor#testClassBeans"));
            out.println("  containsProfileVetoProcessor="
                    + graph.contains("TestsAsBeansProcessor#vetoTestClassesNotMatchingTestProfile"));
            out.println("  containsProfileBeanVetoProcessor="
                    + graph.contains("TestsAsBeansProcessor#vetoTestProfileBeans"));
            out.println("  containsArcRegisterBeans=" + graph.contains("ArcProcessor#registerBeans"));
            out.println("  lines=" + graph.lines().count());
        } catch (java.io.IOException | RuntimeException exception) {
            out.println("  entries=<unavailable: " + exception.getClass().getSimpleName() + ">");
        }
    }
}
