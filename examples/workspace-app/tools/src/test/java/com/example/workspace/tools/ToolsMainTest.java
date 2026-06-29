package com.example.workspace.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ToolsMainTest {
    @Test
    void formatsReleaseNotesCommand() {
        assertEquals("workspace tools: release notes ready", ToolsMain.message("release-notes", new String[0]));
    }

    @Test
    void formatsEchoArguments() {
        assertEquals("workspace tools: alpha beta", ToolsMain.message("echo", new String[] {"alpha", "beta"}));
    }
}
