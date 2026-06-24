package com.zolt.arch;

import static com.zolt.arch.ArchitectureDiagnostics.describe;
import static com.zolt.arch.ContextFootprintBudgetSupport.packageFootprints;
import static com.zolt.arch.ContextFootprintBudgetSupport.readBudgets;
import static com.zolt.arch.ContextFootprintBudgetSupport.violation;
import static com.zolt.arch.ContextFootprintBudgetSupport.writeSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.arch.ContextFootprintBudgetSupport.Budget;
import com.zolt.arch.ContextFootprintBudgetSupport.PackageFootprint;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ContextFootprintBudgetTest {
    private static final Path BUDGETS =
            RepositoryPaths.appRoot().resolve("src/test/resources/com/zolt/arch/context-footprint-budgets.txt");

    @Test
    void packageFootprintsStayWithinBudgets() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Budget budget : readBudgets(BUDGETS)) {
            for (PackageFootprint footprint : packageFootprints(budget)) {
                if (footprint.files() > budget.maxFiles() || footprint.lines() > budget.maxLines()) {
                    violations.add(violation(footprint, budget));
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                () -> "Context footprint budget violations:\n"
                        + describe(violations)
                        + "\nRun `scripts/report-context-footprint` and split the package/root or update the budget with a planned policy change.");
    }

    @Test
    void scannerGroupsFilesByRootAndPackage(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java");
        writeSource(sourceRoot.resolve("com/example/alpha/First.java"), "com.example.alpha", 3);
        writeSource(sourceRoot.resolve("com/example/alpha/Second.java"), "com.example.alpha", 5);
        writeSource(sourceRoot.resolve("com/example/beta/Beta.java"), "com.example.beta", 7);

        assertEquals(
                List.of(
                        new PackageFootprint(RepositoryPaths.displayPath(sourceRoot), "com.example.alpha", 2, 8),
                        new PackageFootprint(RepositoryPaths.displayPath(sourceRoot), "com.example.beta", 1, 7)),
                packageFootprints(List.of(new Budget(sourceRoot, 10, 20))));
    }

    @Test
    void scannerExpandsWildcardRootPatterns(@TempDir Path tempDir) throws IOException {
        Path alphaRoot = tempDir.resolve("modules/alpha/src/main/java");
        Path betaRoot = tempDir.resolve("modules/beta/src/main/java");
        writeSource(alphaRoot.resolve("com/example/Alpha.java"), "com.example.alpha", 3);
        writeSource(betaRoot.resolve("com/example/Beta.java"), "com.example.beta", 5);

        assertEquals(
                List.of(
                        new PackageFootprint(RepositoryPaths.displayPath(alphaRoot), "com.example.alpha", 1, 3),
                        new PackageFootprint(RepositoryPaths.displayPath(betaRoot), "com.example.beta", 1, 5)),
                packageFootprints(List.of(new Budget(
                        tempDir.resolve("modules/*/src/main/java"),
                        10,
                        20))));
    }

    @Test
    void violationsReportFileAndLineBudgets() {
        PackageFootprint footprint = new PackageFootprint("apps/zolt/src/test/java", "com.zolt.cli", 141, 15001);
        Budget budget = new Budget(Path.of("apps/*/src/test/java"), 140, 15000);

        assertEquals(
                "apps/zolt/src/test/java com.zolt.cli has 141 files and 15001 lines; budget is 140 files and 15000 lines",
                violation(footprint, budget));
    }

    @Test
    void reportScriptRejectsInvalidLimit() throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("scripts/report-context-footprint");
        builder.directory(RepositoryPaths.root().toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("ZOLT_CONTEXT_FOOTPRINT_LIMIT", "zero");
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());

        assertEquals(2, process.waitFor(), output);
        assertTrue(output.contains("ZOLT_CONTEXT_FOOTPRINT_LIMIT must be a positive integer"), output);
    }
}
