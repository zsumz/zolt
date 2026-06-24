package com.zolt.arch;

import static com.zolt.arch.FileSizeBudgetSupport.readAllowlist;
import static com.zolt.arch.FileSizeBudgetSupport.readBudgets;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
