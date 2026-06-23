package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.PackageMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageEvidenceArtifactWriterTest {
    @TempDir
    private Path projectDir;

    @Test
    void writesMainArtifactThenClassifierSortedSupplementalArtifacts() throws IOException {
        Path classes = projectDir.resolve("target/classes");
        Path main = projectDir.resolve("target/demo-0.1.0.jar");
        Path sources = projectDir.resolve("target/demo-0.1.0-sources.jar");
        Path javadoc = projectDir.resolve("target/demo-0.1.0-javadoc.jar");
        Files.createDirectories(main.getParent());
        Files.writeString(main, "main artifact");
        Files.writeString(sources, "sources artifact");
        Files.writeString(javadoc, "javadoc artifact");
        PackageResult result = new PackageResult(
                new BuildResult(Optional.empty(), 0, 0, classes, ""),
                PackageMode.THIN,
                main,
                Optional.empty(),
                Optional.empty(),
                7,
                true,
                List.of());

        StringBuilder json = new StringBuilder();
        PackageEvidenceArtifactWriter.write(json, projectDir, result, List.of(
                new PackageArtifact("sources", sources, 3),
                new PackageArtifact("javadoc", javadoc, 2)));
        String artifacts = json.toString();
        int mainIndex = artifacts.indexOf("\"classifier\": \"main\"");
        int javadocIndex = artifacts.indexOf("\"classifier\": \"javadoc\"");
        int sourcesIndex = artifacts.indexOf("\"classifier\": \"sources\"");

        assertTrue(mainIndex >= 0);
        assertTrue(javadocIndex > mainIndex);
        assertTrue(sourcesIndex > javadocIndex);
        assertTrue(artifacts.contains("\"type\": \"thin\""));
        assertTrue(artifacts.contains("\"entries\": 7"));
        assertTrue(artifacts.contains("\"path\": \"target/demo-0.1.0-javadoc.jar\""));
        assertTrue(artifacts.contains("\"sha256\": \"sha256:"));
    }
}
