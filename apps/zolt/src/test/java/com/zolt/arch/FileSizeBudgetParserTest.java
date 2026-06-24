package com.zolt.arch;

import static com.zolt.arch.FileSizeBudgetSupport.readAllowlist;
import static com.zolt.arch.FileSizeBudgetSupport.readBudgets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.arch.FileSizeBudgetSupport.AllowlistEntry;
import com.zolt.arch.FileSizeBudgetSupport.Budget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileSizeBudgetParserTest {
    @Test
    void budgetFileParserReadsRootsAndThresholds(@TempDir Path tempDir) throws IOException {
        Path budgets = tempDir.resolve("budgets.txt");
        Files.writeString(budgets, """
                # rootPattern|softThreshold|hardThreshold
                apps/*/src/main/java|350|500

                modules/*/src/test/java|450|650
                """);

        assertEquals(
                List.of(
                        new Budget(Path.of("apps/*/src/main/java"), 350, 500),
                        new Budget(Path.of("modules/*/src/test/java"), 450, 650)),
                readBudgets(budgets));
    }

    @Test
    void budgetFileParserRejectsMalformedLines(@TempDir Path tempDir) throws IOException {
        Path budgets = tempDir.resolve("budgets.txt");
        Files.writeString(budgets, "apps/*/src/main/java|350\n");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> readBudgets(budgets));

        assertEquals("Invalid file-size budget line: apps/*/src/main/java|350", exception.getMessage());
    }

    @Test
    void allowlistParserReadsTrackedExceptions(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, """
                # path|maxLines|followUp
                modules/zolt-build/src/test/java/com/zolt/build/LargeTest.java|700|
                """);

        assertEquals(
                Map.of(
                        "modules/zolt-build/src/test/java/com/zolt/build/LargeTest.java",
                        new AllowlistEntry(
                                "modules/zolt-build/src/test/java/com/zolt/build/LargeTest.java",
                                700,
                                "")),
                readAllowlist(allowlist));
    }

    @Test
    void allowlistParserRejectsMalformedLines(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, "modules/zolt-build/src/test/java/com/zolt/build/LargeTest.java|700\n");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> readAllowlist(allowlist));

        assertEquals(
                "Invalid file-size allowlist line: modules/zolt-build/src/test/java/com/zolt/build/LargeTest.java|700",
                exception.getMessage());
    }

    @Test
    void allowlistParserRejectsDuplicatePaths(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, """
                modules/zolt-build/src/test/java/com/zolt/build/LargeTest.java|700|
                modules/zolt-build/src/test/java/com/zolt/build/LargeTest.java|701|
                """);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> readAllowlist(allowlist));

        assertEquals(
                "Duplicate file-size allowlist entry: modules/zolt-build/src/test/java/com/zolt/build/LargeTest.java",
                exception.getMessage());
    }
}
