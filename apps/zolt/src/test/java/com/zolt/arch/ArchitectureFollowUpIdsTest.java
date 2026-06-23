package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ArchitectureFollowUpIdsTest {
    @Test
    void scannerFindsModernFollowUpIds(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("-add-complexity-budget.md"), "#  - Add complexity budget\n");
        Files.writeString(tempDir.resolve("README.md"), "# FollowUps\n");

        assertEquals(Set.of(""), ArchitectureFollowUpIds.read(tempDir));
    }
}
