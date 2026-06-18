package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class UserGlobalConfigDocumentationTest {
    @Test
    void documentsAllowedSectionsRejectedSemanticsAndPrecedence() throws IOException {
        String docs = Files.readString(Path.of("docs/user-global-config.md"));

        assertTrue(docs.contains("## Allowed Sections"));
        assertTrue(docs.contains("[cache]"));
        assertTrue(docs.contains("[repository]"));
        assertTrue(docs.contains("[repositoryOverlays.<id>]"));
        assertTrue(docs.contains("[ui]"));
        assertTrue(docs.contains("## Rejected Semantic Sections"));
        assertTrue(docs.contains("[repositories]"));
        assertTrue(docs.contains("[dependencies]"));
        assertTrue(docs.contains("[platforms]"));
        assertTrue(docs.contains("[package]"));
        assertTrue(docs.contains("[compiler]"));
        assertTrue(docs.contains("[native]"));
        assertTrue(docs.contains("## Precedence"));
        assertTrue(docs.contains("Command flags."));
        assertTrue(docs.contains("Explicit environment variables"));
        assertTrue(docs.contains("Committed project and workspace config"));
        assertTrue(docs.contains("User global config"));
        assertTrue(docs.contains("Built-in defaults"));
    }

    @Test
    void documentsLocalOverlayAndNativeImageBoundaries() throws IOException {
        String docs = Files.readString(Path.of("docs/user-global-config.md"));

        assertTrue(docs.contains("Local overlays are disabled by default."));
        assertTrue(docs.contains("--no-local-overlays"));
        assertTrue(docs.contains("local-overlay:<id>"));
        assertTrue(docs.contains("must reject overlay-origin lockfiles"));
        assertTrue(docs.contains("## Native Image Compatibility"));
        assertTrue(docs.contains("Reuse the existing TOML parser dependency"));
        assertTrue(docs.contains("Avoid service loaders, dynamic class loading, and reflection."));
    }

    @Test
    void recordsImplementationFollowUps() throws IOException {
        String docs = Files.readString(Path.of("docs/user-global-config.md"));
        String parserFollowUp = Files.readString(Path.of("followUps/-implement-user-global-config-parser.md"));
        String diagnosticsFollowUp = Files.readString(Path.of("followUps/-expose-user-global-config-diagnostics.md"));

        assertTrue(docs.contains(""));
        assertTrue(docs.contains(""));
        assertTrue(parserFollowUp.contains("Status: Open"));
        assertTrue(parserFollowUp.contains("Rejected semantic sections fail"));
        assertTrue(diagnosticsFollowUp.contains("Status: Open"));
        assertTrue(diagnosticsFollowUp.contains("Output distinguishes machine preferences from committed project semantics."));
    }
}
