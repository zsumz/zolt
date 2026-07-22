package sh.zolt.build.incremental;

import static org.junit.jupiter.api.Assertions.assertEquals;

import sh.zolt.classpath.Classpath;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IncrementalAnnotationProcessorClassifierTest {
    private static final String SERVICE = "META-INF/services/javax.annotation.processing.Processor";
    private static final String INCREMENTAL = "META-INF/gradle/incremental.annotation.processors";

    private final IncrementalAnnotationProcessorClassifier classifier =
            new IncrementalAnnotationProcessorClassifier();

    @TempDir
    private Path tempDir;

    @Test
    void emptyProcessorClasspathAllowsIncremental() {
        assertEquals("", classifier.fallbackReason(new Classpath(List.of())));
    }

    @Test
    void allIsolatingProcessorsReportGeneratedOutputsUntracked() throws IOException {
        Path dir = processorDir(
                "isolating-dir",
                "com.example.Isolating",
                "com.example.Isolating,isolating");
        assertEquals(
                "processor-generated-outputs-untracked",
                classifier.fallbackReason(new Classpath(List.of(dir))));
    }

    @Test
    void allIsolatingProcessorsInJarReportGeneratedOutputsUntracked() throws IOException {
        Path jar = processorJar(
                "isolating.jar",
                "com.example.Isolating",
                "com.example.Isolating,ISOLATING");
        assertEquals(
                "processor-generated-outputs-untracked",
                classifier.fallbackReason(new Classpath(List.of(jar))));
    }

    @Test
    void aggregatingProcessorReportsAggregating() throws IOException {
        Path dir = processorDir(
                "aggregating-dir",
                "com.example.Aggregating",
                "com.example.Aggregating,aggregating");
        assertEquals(
                "processor-aggregating",
                classifier.fallbackReason(new Classpath(List.of(dir))));
    }

    @Test
    void dynamicProcessorReportsDynamic() throws IOException {
        Path dir = processorDir(
                "dynamic-dir",
                "com.example.Dynamic",
                "com.example.Dynamic,dynamic");
        assertEquals(
                "processor-dynamic",
                classifier.fallbackReason(new Classpath(List.of(dir))));
    }

    @Test
    void serviceProcessorWithoutMetadataReportsMissing() throws IOException {
        Path dir = processorDir("no-metadata-dir", "com.example.Undeclared", null);
        assertEquals(
                "processor-metadata-missing",
                classifier.fallbackReason(new Classpath(List.of(dir))));
    }

    @Test
    void oneNonIsolatingProcessorOnPathBlocksIncremental() throws IOException {
        Path isolating = processorJar(
                "isolating.jar",
                "com.example.Isolating",
                "com.example.Isolating,isolating");
        Path aggregating = processorDir(
                "aggregating-dir",
                "com.example.Aggregating",
                "com.example.Aggregating,aggregating");
        assertEquals(
                "processor-aggregating",
                classifier.fallbackReason(new Classpath(List.of(isolating, aggregating))));
    }

    @Test
    void processorMissingAnIsolatingDeclarationBlocksIncremental() throws IOException {
        Path dir = processorDir(
                "partial-dir",
                "com.example.Isolating\ncom.example.Extra",
                "com.example.Isolating,isolating");
        assertEquals(
                "processor-metadata-missing",
                classifier.fallbackReason(new Classpath(List.of(dir))));
    }

    private Path processorDir(String name, String serviceContent, String incrementalContent) throws IOException {
        Path dir = tempDir.resolve(name);
        write(dir.resolve(SERVICE), serviceContent + "\n");
        if (incrementalContent != null) {
            write(dir.resolve(INCREMENTAL), incrementalContent + "\n");
        }
        return dir;
    }

    private Path processorJar(String name, String serviceContent, String incrementalContent) throws IOException {
        Path jar = tempDir.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            writeEntry(zip, SERVICE, serviceContent + "\n");
            if (incrementalContent != null) {
                writeEntry(zip, INCREMENTAL, incrementalContent + "\n");
            }
        }
        return jar;
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream output = Files.newOutputStream(path)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
