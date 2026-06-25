package com.zolt.quarkus;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

final class QuarkusGeneratedArcBytecodeDiagnostic {
    static final String GENERATED_BYTECODE_JAR_PROPERTY =
            "zolt.quarkus.generated-bytecode-jar";

    private final PrintStream out;

    QuarkusGeneratedArcBytecodeDiagnostic(PrintStream out) {
        this.out = out;
    }

    void write(List<String> testClasses) {
        String generatedBytecodeJar = System.getProperty(GENERATED_BYTECODE_JAR_PROPERTY, "");
        if (generatedBytecodeJar.isBlank()) {
            return;
        }
        Path path = Path.of(generatedBytecodeJar).toAbsolutePath().normalize();
        out.println("Zolt Quarkus generated Arc bytecode diagnostic:");
        out.println("  file=" + path);
        if (!Files.isRegularFile(path)) {
            out.println("  entries=<missing>");
            return;
        }
        try (ZipFile zip = new ZipFile(path.toFile())) {
            out.println("  generatedArcBeanClasses=count=" + beanClassCount(zip));
            if (testClasses == null || testClasses.isEmpty()) {
                out.println("  selectedTestBeans=<none>");
                return;
            }
            out.println("  selectedTestBeans=" + selectedTestBeans(zip, testClasses));
        } catch (IOException | RuntimeException exception) {
            out.println("  entries=<unavailable: " + exception.getClass().getSimpleName() + ">");
        }
    }

    private static long beanClassCount(ZipFile zip) {
        return zip.stream()
                .filter(entry -> !entry.isDirectory())
                .map(entry -> entry.getName())
                .filter(name -> name.endsWith("_Bean.class"))
                .count();
    }

    private static String selectedTestBeans(ZipFile zip, List<String> testClasses) {
        return testClasses.stream()
                .map(testClass -> testClass + "[beanClass=" + beanClass(testClass)
                        + ",present=" + (zip.getEntry(beanClass(testClass)) != null) + "]")
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String beanClass(String className) {
        return className.replace('.', '/') + "_Bean.class";
    }
}
