package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.IdeModelCommandJsonTestSupport.cacheRoot;
import static com.zolt.cli.IdeModelCommandJsonTestSupport.currentJavaMajorVersionValue;
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

final class IdeModelCommandJsonTest {
    @TempDir
    private Path tempDir;

    @Test
    void ideModelPrintsCoreModelJsonFromProjectAndLockfile() throws IOException {
        Path projectDir = writeProject(tempDir);
        Path cacheRoot = cacheRoot(tempDir);
        writeLockfile(projectDir);

        CommandResult result = execute(
                "ide",
                "model",
                "--directory", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "--format", "json");

        Path projectRoot = root(projectDir);
        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        String json = result.stdout();
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"project\": {\n    \"name\": \"demo\""));
        assertTrue(json.contains("\"java\": {\n    \"version\": \"" + currentJavaMajorVersionValue()));
        assertTrue(json.contains("\"compiler\": {\n    \"release\": null"));
        assertTrue(json.contains("\"package\": {\n    \"mode\": \"thin\""));
        assertTrue(json.contains("\"paths\": {\n    \"root\": \"" + root(projectDir)));
        assertTrue(json.contains("\"sourceRoots\": ["));
        assertTrue(json.contains("\"generatedSources\": []"));
        assertTrue(json.contains("\"resourceRoots\": ["));
        assertTrue(json.contains("\"outputs\": {\n    \"mainClasses\": \""));
        assertTrue(json.contains("\"dependencies\": {\n    \"versionAliases\": {}"));
    }
}
