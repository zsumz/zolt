package sh.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class FileSizeBudgetTest {
    private static final int MAIN_SOURCE_MAX_LINES = 350;
    private static final int TEST_SOURCE_MAX_LINES = 450;
    private static final String ALLOWLIST = "apps/zolt/src/test/resources/sh/zolt/arch/file-size-allowlist.txt";

    @Test
    void javaFilesStayWithinBudgetOrHaveExplicitAllowance() throws IOException {
        Path root = ArchGuardrailSupport.repositoryRoot();
        ArchGuardrailSupport.PathAllowlist allowlist = ArchGuardrailSupport.pathAllowlist(root, ALLOWLIST);
        Set<String> matchedAllowlistPaths = new TreeSet<>();
        List<String> problems = new ArrayList<>();

        checkFiles(
                root,
                ArchGuardrailSupport.mainJavaFiles(root),
                "main source",
                MAIN_SOURCE_MAX_LINES,
                allowlist,
                matchedAllowlistPaths,
                problems);
        checkFiles(
                root,
                ArchGuardrailSupport.testJavaFiles(root),
                "test source",
                TEST_SOURCE_MAX_LINES,
                allowlist,
                matchedAllowlistPaths,
                problems);

        for (String allowedPath : allowlist.paths()) {
            if (!matchedAllowlistPaths.contains(allowedPath)) {
                problems.add("Stale file-size allowance: " + allowedPath
                        + " is no longer over budget; remove it from " + ALLOWLIST + ".");
            }
        }

        assertTrue(problems.isEmpty(), () -> fileSizeMessage(problems));
    }

    private static void checkFiles(
            Path root,
            List<Path> files,
            String sourceKind,
            int maxLines,
            ArchGuardrailSupport.PathAllowlist allowlist,
            Set<String> matchedAllowlistPaths,
            List<String> problems) throws IOException {
        for (Path file : files) {
            int lines = lineCount(file);
            if (lines <= maxLines) {
                continue;
            }
            String relativePath = ArchGuardrailSupport.relativePath(root, file);
            if (allowlist.contains(relativePath)) {
                matchedAllowlistPaths.add(relativePath);
                continue;
            }
            problems.add(relativePath + " has " + lines + " lines; " + sourceKind
                    + " budget is " + maxLines + " lines.");
        }
    }

    private static int lineCount(Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file)) {
            return Math.toIntExact(lines.count());
        }
    }

    private static String fileSizeMessage(List<String> problems) {
        List<String> lines = new ArrayList<>();
        lines.add("Java file-size guardrail drift.");
        lines.add("Main Java files must stay at or below " + MAIN_SOURCE_MAX_LINES + " lines.");
        lines.add("Test Java files must stay at or below " + TEST_SOURCE_MAX_LINES + " lines.");
        lines.add("Split new behavior into focused companion classes, or add a temporary allowance with a reason.");
        lines.addAll(problems);
        return String.join(System.lineSeparator(), lines);
    }
}
