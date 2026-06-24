package com.zolt.arch;

import static com.zolt.arch.ComplexityBudgetSupport.readAllowlist;
import static com.zolt.arch.ComplexityBudgetSupport.readBudgets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.arch.ComplexityBudgetSupport.AllowlistEntry;
import com.zolt.arch.ComplexityBudgetSupport.Budget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ComplexityBudgetParserTest {
    @Test
    void budgetFileParserReadsRootPatternsAndThresholds(@TempDir Path tempDir) throws IOException {
        Path budgets = tempDir.resolve("budgets.txt");
        Files.writeString(budgets, """
                # rootPattern|maxImports|maxPublicMethods|maxConstructorParameters|maxNestedTypes
                apps/*/src/main/java|45|30|10|20

                modules/*/src/main/java|40|25|8|12
                """);

        assertEquals(
                List.of(
                        new Budget(Path.of("apps/*/src/main/java"), 45, 30, 10, 20),
                        new Budget(Path.of("modules/*/src/main/java"), 40, 25, 8, 12)),
                readBudgets(budgets));
    }

    @Test
    void budgetFileParserRejectsMalformedLines(@TempDir Path tempDir) throws IOException {
        Path budgets = tempDir.resolve("budgets.txt");
        Files.writeString(budgets, "apps/*/src/main/java|45|30|10\n");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> readBudgets(budgets));

        assertEquals("Invalid complexity budget line: apps/*/src/main/java|45|30|10", exception.getMessage());
    }

    @Test
    void allowlistParserReadsTrackedExceptions(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, """
                # path|maxImports|maxPublicMethods|maxConstructorParameters|maxNestedTypes|followUp|reason
                modules/zolt-build/src/main/java/com/zolt/build/Large.java|50|35|12|22||legacy coordinator
                """);

        assertEquals(
                Map.of(
                        "modules/zolt-build/src/main/java/com/zolt/build/Large.java",
                        new AllowlistEntry(
                                "modules/zolt-build/src/main/java/com/zolt/build/Large.java",
                                50,
                                35,
                                12,
                                22,
                                "",
                                "legacy coordinator")),
                readAllowlist(allowlist));
    }

    @Test
    void allowlistParserRejectsMalformedLines(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, "modules/zolt-build/src/main/java/com/zolt/build/Large.java|50|35|12|22|\n");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> readAllowlist(allowlist));

        assertEquals(
                "Invalid complexity allowlist line: modules/zolt-build/src/main/java/com/zolt/build/Large.java|50|35|12|22|",
                exception.getMessage());
    }

    @Test
    void allowlistParserRejectsDuplicatePaths(@TempDir Path tempDir) throws IOException {
        Path allowlist = tempDir.resolve("allowlist.txt");
        Files.writeString(allowlist, """
                modules/zolt-build/src/main/java/com/zolt/build/Large.java|50|35|12|22||legacy coordinator
                modules/zolt-build/src/main/java/com/zolt/build/Large.java|51|36|13|23||still large
                """);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> readAllowlist(allowlist));

        assertEquals(
                "Duplicate complexity allowlist entry: modules/zolt-build/src/main/java/com/zolt/build/Large.java",
                exception.getMessage());
    }
}
