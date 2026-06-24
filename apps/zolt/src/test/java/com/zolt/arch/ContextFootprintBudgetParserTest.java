package com.zolt.arch;

import static com.zolt.arch.ContextFootprintBudgetSupport.readBudgets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.arch.ContextFootprintBudgetSupport.Budget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ContextFootprintBudgetParserTest {
    @Test
    void budgetFileParserReadsRootsAndThresholds(@TempDir Path tempDir) throws IOException {
        Path budgets = tempDir.resolve("budgets.txt");
        Files.writeString(budgets, """
                # rootPattern|maxFiles|maxLines
                apps/*/src/main/java|140|15000

                modules/*/src/test/java|120|12000
                """);

        assertEquals(
                List.of(
                        new Budget(Path.of("apps/*/src/main/java"), 140, 15000),
                        new Budget(Path.of("modules/*/src/test/java"), 120, 12000)),
                readBudgets(budgets));
    }

    @Test
    void budgetFileParserRejectsMalformedLines(@TempDir Path tempDir) throws IOException {
        Path budgets = tempDir.resolve("budgets.txt");
        Files.writeString(budgets, "apps/*/src/main/java|140\n");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> readBudgets(budgets));

        assertEquals("Invalid context footprint budget line: apps/*/src/main/java|140", exception.getMessage());
    }
}
