package sh.zolt.quarkus.annotation.diagnostic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusGeneratedArcBytecodeDiagnosticTest {
    @TempDir
    private Path tempDir;

    @AfterEach
    void clearProperty() {
        System.clearProperty(QuarkusGeneratedArcBytecodeDiagnostic.GENERATED_BYTECODE_JAR_PROPERTY);
    }

    @Test
    void writesSelectedTestBeanPresence() throws Exception {
        Path generatedBytecode = tempDir.resolve("generated-bytecode.jar");
        writeZip(
                generatedBytecode,
                "com/example/AlphaTest_Bean.class",
                "com/example/Resource_Bean.class");
        System.setProperty(
                QuarkusGeneratedArcBytecodeDiagnostic.GENERATED_BYTECODE_JAR_PROPERTY,
                generatedBytecode.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        diagnostic(out).write(List.of("com.example.AlphaTest", "com.example.BetaTest"));

        String output = output(out);
        assertTrue(output.contains("Zolt Quarkus generated Arc bytecode diagnostic:"));
        assertTrue(output.contains("generatedArcBeanClasses=count=2"));
        assertTrue(output.contains("com.example.AlphaTest[beanClass=com/example/AlphaTest_Bean.class,present=true]"));
        assertTrue(output.contains("com.example.BetaTest[beanClass=com/example/BetaTest_Bean.class,present=false]"));
    }

    @Test
    void writesMissingGeneratedBytecodeJar() {
        Path generatedBytecode = tempDir.resolve("missing-generated-bytecode.jar");
        System.setProperty(
                QuarkusGeneratedArcBytecodeDiagnostic.GENERATED_BYTECODE_JAR_PROPERTY,
                generatedBytecode.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        diagnostic(out).write(List.of("com.example.AlphaTest"));

        String output = output(out);
        assertTrue(output.contains("file=" + generatedBytecode.toAbsolutePath().normalize()));
        assertTrue(output.contains("entries=<missing>"));
    }

    private static QuarkusGeneratedArcBytecodeDiagnostic diagnostic(ByteArrayOutputStream out) {
        return new QuarkusGeneratedArcBytecodeDiagnostic(new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    private static void writeZip(Path file, String... entries) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(java.nio.file.Files.newOutputStream(file))) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(new byte[] {0});
                zip.closeEntry();
            }
        }
    }

    private static String output(ByteArrayOutputStream out) {
        return out.toString(StandardCharsets.UTF_8);
    }
}
