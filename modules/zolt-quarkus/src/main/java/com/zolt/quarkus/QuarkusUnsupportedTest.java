package com.zolt.quarkus;

import java.nio.file.Path;

public record QuarkusUnsupportedTest(
        Path classFile,
        Path relativePath,
        String annotationName,
        boolean annotationRunnerSupported) {
    public QuarkusUnsupportedTest {
        if (classFile == null) {
            throw new QuarkusPlanException("Quarkus unsupported test requires a class file.");
        }
        if (relativePath == null) {
            throw new QuarkusPlanException("Quarkus unsupported test requires a relative path.");
        }
        if (annotationName == null || annotationName.isBlank()) {
            throw new QuarkusPlanException("Quarkus unsupported test requires an annotation name.");
        }
    }

    public QuarkusUnsupportedTest(
            Path classFile,
            Path relativePath,
            String annotationName) {
        this(classFile, relativePath, annotationName, false);
    }

    public boolean blocksAnnotationRunner() {
        return !annotationRunnerSupported;
    }
}
