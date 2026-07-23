package sh.zolt.javac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavacWorkerAttributionTest {
    @TempDir
    Path tempDir;

    @Test
    void attributedCompileMapsGeneratedSourceToOriginatingType() throws Exception {
        Path processorClasses = WorkerAttributionFixture.compileProcessor(tempDir);
        Path source = writeSource("Widget", "public class Widget {}\n");
        Path output = Files.createDirectories(tempDir.resolve("classes"));
        Path generated = Files.createDirectories(tempDir.resolve("generated"));

        WorkerAttributionFixture.Attribution attribution = compileAttributed(List.of(
                "-d", output.toString(),
                "-s", generated.toString(),
                "-processorpath", processorClasses.toString(),
                source.toString()));

        assertEquals(0, attribution.exitCode(), attribution.diagnostics());
        assertTrue(attribution.present());
        assertFalse(attribution.unattributed(), "well-formed isolating output should be fully attributed");
        assertTrue(Files.isRegularFile(generated.resolve("gen/WidgetInfo.java")));
        WorkerAttributionFixture.Entry widgetInfo = attribution.entries().stream()
                .filter(entry -> entry.path().endsWith("WidgetInfo.java"))
                .findFirst()
                .orElseThrow();
        assertEquals(GeneratedFileRecord.KIND_SOURCE, widgetInfo.kind());
        assertEquals("gen.WidgetInfo", widgetInfo.createdType());
        assertEquals(List.of("Widget"), widgetInfo.originatingTypes());
    }

    @Test
    void attributedCompileFlagsOutputGeneratedWithoutOriginatingElement() throws Exception {
        Path processorClasses = WorkerAttributionFixture.compileProcessor(tempDir);
        Path source = writeSource("Gadget", "public class Gadget {}\n");
        Path output = Files.createDirectories(tempDir.resolve("classes"));
        Path generated = Files.createDirectories(tempDir.resolve("generated"));

        WorkerAttributionFixture.Attribution attribution = compileAttributed(List.of(
                "-Azolt.omitOriginating=true",
                "-d", output.toString(),
                "-s", generated.toString(),
                "-processorpath", processorClasses.toString(),
                source.toString()));

        assertEquals(0, attribution.exitCode(), attribution.diagnostics());
        assertTrue(attribution.present());
        assertTrue(attribution.unattributed(), "output generated without originating elements must be unattributed");
    }

    @Test
    void attributedCompileMapsGeneratedResourceToOriginatingType() throws Exception {
        Path processorClasses = WorkerAttributionFixture.compileResourceProcessor(tempDir);
        Path source = writeSource("Widget", "public class Widget {}\n");
        Path output = Files.createDirectories(tempDir.resolve("classes"));
        Path generated = Files.createDirectories(tempDir.resolve("generated"));

        WorkerAttributionFixture.Attribution attribution = compileAttributed(List.of(
                "-d", output.toString(),
                "-s", generated.toString(),
                "-processorpath", processorClasses.toString(),
                source.toString()));

        assertEquals(0, attribution.exitCode(), attribution.diagnostics());
        assertTrue(attribution.present());
        assertFalse(attribution.unattributed(), "an isolating resource with an originating element is attributed");
        assertTrue(Files.isRegularFile(output.resolve("gen/Widget.meta")));
        WorkerAttributionFixture.Entry meta = attribution.entries().stream()
                .filter(entry -> entry.path().endsWith("Widget.meta"))
                .findFirst()
                .orElseThrow();
        assertEquals(GeneratedFileRecord.KIND_RESOURCE, meta.kind());
        assertEquals("", meta.createdType());
        assertEquals(List.of("Widget"), meta.originatingTypes());
    }

    @Test
    void attributedCompileFlagsResourceGeneratedWithoutOriginatingElement() throws Exception {
        Path processorClasses = WorkerAttributionFixture.compileResourceProcessor(tempDir);
        Path source = writeSource("Gadget", "public class Gadget {}\n");
        Path output = Files.createDirectories(tempDir.resolve("classes"));
        Path generated = Files.createDirectories(tempDir.resolve("generated"));

        WorkerAttributionFixture.Attribution attribution = compileAttributed(List.of(
                "-Azolt.omitOriginating=true",
                "-d", output.toString(),
                "-s", generated.toString(),
                "-processorpath", processorClasses.toString(),
                source.toString()));

        assertEquals(0, attribution.exitCode(), attribution.diagnostics());
        assertTrue(attribution.present());
        assertTrue(attribution.unattributed(), "a resource generated without originating elements must be unattributed");
    }

    private Path writeSource(String name, String body) throws Exception {
        Path source = tempDir.resolve(name + ".java");
        Files.writeString(source, body);
        return source;
    }

    private static WorkerAttributionFixture.Attribution compileAttributed(List<String> arguments) throws Exception {
        ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();
        try (DataOutputStream request = new DataOutputStream(requestBytes)) {
            request.writeInt(WorkerCompileProtocol.KIND_COMPILE_ATTRIBUTED);
            request.writeInt(arguments.size());
            for (String argument : arguments) {
                WorkerCompileProtocol.writeString(request, argument);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int runExit = JavacWorkerMain.run(
                new ByteArrayInputStream(requestBytes.toByteArray()),
                output,
                new PrintStream(error, true, StandardCharsets.UTF_8));
        assertEquals(0, runExit, error.toString(StandardCharsets.UTF_8));
        return WorkerAttributionFixture.parse(output.toByteArray());
    }
}
