package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

final class ModernConsoleOutputDocumentationTest {
    @Test
    void modernConsoleOutputDesignNamesColorModesCommandFamiliesAndNonGoals() throws IOException {
        String design = Files.readString(RepositoryPaths.root().resolve("docs/modern-console-output.md"));

        assertTrue(design.contains("sharp infrastructure tool"));
        assertTrue(design.contains("Cargo is a useful reference"));
        assertTrue(design.contains("uv is also useful inspiration for the control surface around output"));
        assertTrue(design.contains("color and formatting are additive"));
        assertTrue(design.contains("--color=auto|always|never"));
        assertTrue(design.contains("NO_COLOR"));
        assertTrue(design.contains("Machine-readable formats such as `--format json` ignore color"));
        assertTrue(design.contains("Resolve output should answer"));
        assertTrue(design.contains("Build output should keep compile, resources, generated sources, and skipped work visible"));
        assertTrue(design.contains("Quality checks benefit from a compact status table"));
        assertTrue(design.contains("Use short blocks for failures"));
        assertTrue(design.contains("No full-screen terminal UI"));
        assertTrue(design.contains("No progress bars or spinners in MVP"));
    }

    @Test
    void docsIndexLinksModernConsoleOutputDesign() throws IOException {
        String docsIndex = Files.readString(RepositoryPaths.root().resolve("docs/README.md"));
        String consoleOutput = Files.readString(RepositoryPaths.root().resolve("docs/console-output.md"));
        String followUpIndex = Files.readString(RepositoryPaths.root().resolve("followUps/README.md"));

        assertTrue(docsIndex.contains("`modern-console-output.md`"));
        assertTrue(consoleOutput.contains("`modern-console-output.md` defines the follow-on human output design"));
        assertTrue(followUpIndex.contains("**M29** — Modern console output"));
    }
}
