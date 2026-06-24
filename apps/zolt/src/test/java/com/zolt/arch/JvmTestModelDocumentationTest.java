package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

final class JvmTestModelDocumentationTest {
    @Test
    void deterministicTestOutputNamesCurrentColorModeContract() throws IOException {
        String model = Files.readString(RepositoryPaths.root().resolve("docs/jvm-test-model.md"));

        assertTrue(model.contains("`zolt test` follows the global color modes in `docs/console-output.md`"));
        assertTrue(model.contains("`auto`, `always`, and `never`"));
        assertTrue(model.contains("Non-interactive and color-disabled output stays"));
        assertFalse(model.contains("When color support arrives"));
    }

    @Test
    void docsIndexLinksJvmTestModel() throws IOException {
        String docsIndex = Files.readString(RepositoryPaths.root().resolve("docs/README.md"));

        assertTrue(docsIndex.contains("`jvm-test-model.md`"));
    }
}
