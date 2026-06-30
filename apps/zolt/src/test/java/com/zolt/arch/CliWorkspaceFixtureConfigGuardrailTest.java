package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CliWorkspaceFixtureConfigGuardrailTest {
    private static final Path CLI_TEST_ROOT = RepositoryPaths.appRoot().resolve("src/test/java/com/zolt/cli");
    private static final String COMPATIBILITY_FIXTURE =
            "apps/zolt/src/test/java/com/zolt/cli/workspace/WorkspaceResolveCommandTest.java";

    @Test
    void ordinaryCliWorkspaceFixturesUseRootZoltToml() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path javaFile : ArchitectureSourceFiles.javaFiles(List.of(CLI_TEST_ROOT))) {
            String displayPath = RepositoryPaths.displayPath(javaFile);
            List<String> lines = Files.readAllLines(javaFile);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.contains("zolt-workspace.toml") && !allowedCompatibilityReference(displayPath, line)) {
                    violations.add(displayPath + ":" + (index + 1));
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                () -> "Ordinary CLI workspace fixtures should write root zolt.toml with [workspace].\n"
                        + "Use zolt-workspace.toml only in explicit compatibility coverage or negative assertions.\n"
                        + "Unexpected references:\n"
                        + describe(violations));
    }

    private static boolean allowedCompatibilityReference(String displayPath, String line) {
        if (line.contains("assertFalse") && line.contains("Files.exists")) {
            return true;
        }
        return COMPATIBILITY_FIXTURE.equals(displayPath) && line.contains("Files.writeString");
    }

    private static String describe(List<String> violations) {
        StringBuilder description = new StringBuilder();
        for (String violation : violations) {
            description.append("- ").append(violation).append('\n');
        }
        return description.toString();
    }
}
