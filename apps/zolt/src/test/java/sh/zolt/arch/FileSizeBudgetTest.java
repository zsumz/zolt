package sh.zolt.arch;

import static sh.zolt.arch.ArchitectureDiagnostics.describe;
import static sh.zolt.arch.FileSizeBudgetSupport.filesAboveHardThreshold;
import static sh.zolt.arch.FileSizeBudgetSupport.filesAboveSoftThreshold;
import static sh.zolt.arch.FileSizeBudgetSupport.readAllowlist;
import static sh.zolt.arch.FileSizeBudgetSupport.readBudgets;
import static sh.zolt.arch.FileSizeBudgetSupport.writeLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.arch.FileSizeBudgetSupport.AllowlistEntry;
import sh.zolt.arch.FileSizeBudgetSupport.Budget;
import sh.zolt.arch.FileSizeBudgetSupport.SourceFileSize;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileSizeBudgetTest {
    private static final Path ALLOWLIST =
            RepositoryPaths.appRoot().resolve("src/test/resources/sh/zolt/arch/file-size-allowlist.txt");
    private static final Path BUDGETS =
            RepositoryPaths.appRoot().resolve("src/test/resources/sh/zolt/arch/file-size-budgets.txt");

    @Test
    void javaFilesStayBelowSoftThresholdsOrHaveExplicitAllowance() throws IOException {
        Map<String, AllowlistEntry> allowlist = readAllowlist(ALLOWLIST);
        Map<String, SourceFileSize> oversizedFiles = filesAboveSoftThreshold(readBudgets(BUDGETS));
        List<String> violations = new ArrayList<>();

        for (SourceFileSize fileSize : oversizedFiles.values()) {
            AllowlistEntry entry = allowlist.get(fileSize.path());
            if (entry == null) {
                violations.add(fileSize.path()
                        + " has "
                        + fileSize.lines()
                        + " lines and needs an explicit file-size allowance");
            } else if (fileSize.lines() > entry.maxLines()) {
                violations.add(fileSize.path()
                        + " grew from allowlisted max "
                        + entry.maxLines()
                        + " to "
                        + fileSize.lines()
                        + " lines ["
                        + entry.reason()
                        + "]");
            }
        }

        List<String> staleEntries = allowlist.keySet().stream()
                .filter(path -> !oversizedFiles.containsKey(path))
                .sorted()
                .toList();
        for (String staleEntry : staleEntries) {
            violations.add(staleEntry + " is no longer above the soft threshold; remove the allowlist entry");
        }

        assertTrue(
                violations.isEmpty(),
                () -> "File-size soft threshold violations:\n"
                        + describe(violations)
                        + "\nSplit the file or add a capped, reasoned allowance.");
    }

    @Test
    void hardThresholdFilesAreExplicitlyAllowlistedAndDoNotGrow() throws IOException {
        Map<String, AllowlistEntry> allowlist = readAllowlist(ALLOWLIST);
        Map<String, SourceFileSize> oversizedFiles = filesAboveHardThreshold(readBudgets(BUDGETS));
        List<String> violations = new ArrayList<>();

        for (SourceFileSize fileSize : oversizedFiles.values()) {
            AllowlistEntry entry = allowlist.get(fileSize.path());
            if (entry == null) {
                violations.add(fileSize.path()
                        + " has "
                        + fileSize.lines()
                        + " lines and needs an explicit file-size allowance");
            } else if (fileSize.lines() > entry.maxLines()) {
                violations.add(fileSize.path()
                        + " grew from allowlisted max "
                        + entry.maxLines()
                        + " to "
                        + fileSize.lines()
                        + " lines ["
                        + entry.reason()
                        + "]");
            }
        }

        assertTrue(
                violations.isEmpty(),
                () -> "File-size budget violations:\n"
                        + describe(violations)
                        + "\nSplit the file or reduce the allowlisted maximum once it shrinks.");
    }

    @Test
    void scannerFindsFilesAboveHardThreshold(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/main/java");
        writeLines(sourceRoot.resolve("Small.java"), 3);
        writeLines(sourceRoot.resolve("Large.java"), 6);

        assertEquals(
                Map.of(
                        RepositoryPaths.displayPath(sourceRoot.resolve("Large.java")),
                        new SourceFileSize(RepositoryPaths.displayPath(sourceRoot.resolve("Large.java")), 6)),
                filesAboveHardThreshold(List.of(new Budget(sourceRoot, 4, 5))));
    }

    @Test
    void scannerFindsFilesAboveSoftThreshold(@TempDir Path tempDir) throws IOException {
        Path sourceRoot = tempDir.resolve("src/test/java");
        writeLines(sourceRoot.resolve("SmallTest.java"), 3);
        writeLines(sourceRoot.resolve("LargeTest.java"), 5);

        assertEquals(
                Map.of(
                        RepositoryPaths.displayPath(sourceRoot.resolve("LargeTest.java")),
                        new SourceFileSize(RepositoryPaths.displayPath(sourceRoot.resolve("LargeTest.java")), 5)),
                filesAboveSoftThreshold(List.of(new Budget(sourceRoot, 4, 10))));
    }

    @Test
    void scannerExpandsWildcardRootPatterns(@TempDir Path tempDir) throws IOException {
        Path alphaSourceRoot = tempDir.resolve("modules/alpha/src/main/java");
        Path betaSourceRoot = tempDir.resolve("modules/beta/src/main/java");
        writeLines(alphaSourceRoot.resolve("Small.java"), 3);
        writeLines(betaSourceRoot.resolve("Large.java"), 5);

        assertEquals(
                Map.of(
                        RepositoryPaths.displayPath(betaSourceRoot.resolve("Large.java")),
                        new SourceFileSize(RepositoryPaths.displayPath(betaSourceRoot.resolve("Large.java")), 5)),
                filesAboveSoftThreshold(List.of(new Budget(
                        tempDir.resolve("modules/*/src/main/java"),
                        4,
                        10))));
    }

}
