package sh.zolt.arch;

import static sh.zolt.arch.ArchitectureDiagnostics.describe;
import static sh.zolt.arch.ContextFootprintBudgetSupport.packageFootprints;
import static sh.zolt.arch.ContextFootprintBudgetSupport.readBudgets;
import static sh.zolt.arch.ContextFootprintBudgetSupport.violation;
import static sh.zolt.arch.ContextFootprintBudgetSupport.writeSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.arch.ContextFootprintBudgetSupport.Budget;
import sh.zolt.arch.ContextFootprintBudgetSupport.PackageFootprint;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ContextFootprintBudgetTest {
    private static final Path BUDGETS =
            RepositoryPaths.appRoot().resolve("src/test/resources/sh/zolt/arch/context-footprint-budgets.txt");

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
                        + "\nSplit the package/root or tighten the budget once the footprint shrinks.");
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
        PackageFootprint footprint = new PackageFootprint("apps/zolt/src/test/java", "sh.zolt.cli", 141, 15001);
        Budget budget = new Budget(Path.of("apps/*/src/test/java"), 140, 15000);

        assertEquals(
                "apps/zolt/src/test/java sh.zolt.cli has 141 files and 15001 lines; budget is 140 files and 15000 lines",
                violation(footprint, budget));
    }

}
