package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.IdeModelCommandJsonTestSupport.cacheRoot;
import static com.zolt.cli.IdeModelCommandJsonTestSupport.jsonPathValue;
import static com.zolt.cli.IdeModelCommandJsonTestSupport.root;
import static com.zolt.cli.IdeModelCommandJsonTestSupport.writeLockfile;
import static com.zolt.cli.IdeModelCommandJsonTestSupport.writeProject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IdeModelCommandJsonDetailsTest {
    @TempDir
    private Path tempDir;

    @Test
    void ideModelPrintsClasspathFrameworkAndDiagnosticsDetailsInJson() throws IOException {
        Path projectDir = writeProject(tempDir);
        Path cacheRoot = cacheRoot(tempDir);
        writeLockfile(projectDir);

        CommandResult result = execute(
                "ide",
                "model",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "--format", "json");

        Path projectRoot = root(projectDir);
        Path appJar = cacheRoot.toAbsolutePath().normalize().resolve("com/example/app/1.0.0/app-1.0.0.jar");
        Path testJar = cacheRoot.toAbsolutePath().normalize().resolve("com/example/test-lib/1.0.0/test-lib-1.0.0.jar");
        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        String json = result.stdout();
        assertTrue(json.contains("\"classpaths\": {\n    \"compile\": ["));
        assertTrue(json.contains(jsonPathValue(appJar)));
        assertTrue(json.contains(jsonPathValue(testJar)));
        assertTrue(json.contains("\"frameworks\": {\n    \"quarkus\": {"));
        assertTrue(json.contains("\"augmentationStatus\": \"disabled\""));
        assertTrue(json.contains("\"diagnostics\": [\n    {\n      \"severity\": \"error\""));
        assertTrue(json.contains("\"code\": \"LOCKFILE_STALE\""));
        assertTrue(json.contains("\"nextStep\": \"Run zolt resolve.\""));
    }
}
