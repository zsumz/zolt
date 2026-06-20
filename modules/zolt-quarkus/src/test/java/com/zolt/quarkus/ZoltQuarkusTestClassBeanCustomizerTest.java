package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ZoltQuarkusTestClassBeanCustomizerTest {
    @TempDir
    private Path tempDir;

    @Test
    void treatsCompiledMainOutputClassesAsApplicationClasses() throws Exception {
        Path outputDirectory = tempDir.resolve("target/classes");
        Path classFile = outputDirectory.resolve("com/example/HelloResource.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, new byte[] {0});
        String previous = System.getProperty(QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY);
        try {
            System.setProperty(
                    QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY,
                    outputDirectory.toString());

            assertTrue(ZoltQuarkusApplicationClassPredicate.test("com.example.HelloResource"));
            assertFalse(ZoltQuarkusApplicationClassPredicate.test("com.example.MissingResource"));
        } finally {
            restoreMainOutputDirectory(previous);
        }
    }

    @Test
    void ignoresClassesWhenMainOutputDirectoryIsUnavailable() {
        String previous = System.getProperty(QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY);
        try {
            System.clearProperty(QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY);

            assertFalse(ZoltQuarkusApplicationClassPredicate.test("com.example.HelloResource"));
        } finally {
            restoreMainOutputDirectory(previous);
        }
    }

    private static void restoreMainOutputDirectory(String previous) {
        if (previous == null) {
            System.clearProperty(QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY);
            return;
        }
        System.setProperty(QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY, previous);
    }
}
