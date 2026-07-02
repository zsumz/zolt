package sh.zolt.build.incremental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.build.BuildException;
import sh.zolt.build.abi.ClassFileAbi;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IncrementalCompileSourceRecordBuilderTest {
    private final IncrementalCompileSourceRecordBuilder builder = new IncrementalCompileSourceRecordBuilder();

    @TempDir
    private Path projectDir;

    @Test
    void recordsSourceMetadataOwnedClassesAndReferences() throws IOException {
        Path sourceRoot = projectDir.resolve("src/main/java");
        Path source = sourceRoot.resolve("com/example/App.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.example;

                @Deprecated
                public final class App {
                }

                interface LocalApi {
                }
                """);
        Path appClass = projectDir.resolve("target/classes/com/example/App.class");
        Path nestedClass = projectDir.resolve("target/classes/com/example/App$Nested.class");

        List<IncrementalCompileState.SourceRecord> records = builder.sourceRecords(
                projectDir,
                List.of(source),
                List.of(sourceRoot),
                Map.of(),
                List.of(
                        abi("com.example.App", appClass, "App.java", List.of("java.lang.String")),
                        abi("com.example.App$Nested", nestedClass, "App.java", List.of("com.example.Dependency"))),
                path -> "hash:" + path.getFileName());

        assertEquals(1, records.size());
        IncrementalCompileState.SourceRecord record = records.getFirst();
        assertEquals(source.toAbsolutePath().normalize(), record.path());
        assertEquals(sourceRoot.toAbsolutePath().normalize(), record.sourceRoot());
        assertEquals(Optional.empty(), record.generatedSourceStepId());
        assertEquals("hash:App.java", record.contentHash());
        assertEquals("com.example", record.packageName());
        assertEquals(List.of("com.example.App", "com.example.LocalApi"), record.declaredTypes());
        assertEquals(
                List.of(nestedClass.toAbsolutePath().normalize(), appClass.toAbsolutePath().normalize()),
                record.classOutputs());
        assertEquals(List.of("com.example.Dependency", "java.lang.String"), record.referencedClasses());
    }

    @Test
    void recordsNearestGeneratedSourceStep() throws IOException {
        Path generatedRoot = projectDir.resolve("target/generated/sources");
        Path openApiRoot = generatedRoot.resolve("openapi");
        Path source = openApiRoot.resolve("com/example/GeneratedApi.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package com.example;

                public interface GeneratedApi {
                }
                """);

        List<IncrementalCompileState.SourceRecord> records = builder.sourceRecords(
                projectDir,
                List.of(source),
                List.of(generatedRoot, openApiRoot),
                Map.of(
                        generatedRoot, "generated",
                        openApiRoot, "openapi"),
                List.of(),
                path -> "hash:" + path.getFileName());

        IncrementalCompileState.SourceRecord record = records.getFirst();
        assertEquals(openApiRoot.toAbsolutePath().normalize(), record.sourceRoot());
        assertEquals(Optional.of("openapi"), record.generatedSourceStepId());
        assertEquals(List.of("com.example.GeneratedApi"), record.declaredTypes());
    }

    @Test
    void reportsMissingSourceMetadataWithActionableMessage() {
        Path missing = projectDir.resolve("src/main/java/com/example/Missing.java");

        BuildException exception = assertThrows(
                BuildException.class,
                () -> builder.sourceRecords(
                        projectDir,
                        List.of(missing),
                        List.of(projectDir.resolve("src/main/java")),
                        Map.of(),
                        List.of(),
                        path -> "hash"));

        assertTrue(exception.getMessage().contains("Could not read source metadata from"));
        assertTrue(exception.getMessage().contains("Check that the source file is readable"));
    }

    private static ClassFileAbi abi(
            String binaryName,
            Path classFile,
            String sourceFileName,
            List<String> referencedClasses) {
        return new ClassFileAbi(
                binaryName,
                classFile,
                Optional.of(sourceFileName),
                33,
                Optional.of("java.lang.Object"),
                List.of(),
                "abi-" + binaryName,
                "package-abi-" + binaryName,
                referencedClasses);
    }
}
