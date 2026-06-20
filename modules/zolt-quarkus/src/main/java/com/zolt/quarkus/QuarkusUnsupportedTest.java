package com.zolt.quarkus;

import java.nio.file.Path;

public record QuarkusUnsupportedTest(
        Path classFile,
        Path relativePath,
        String annotationName) {
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
}
