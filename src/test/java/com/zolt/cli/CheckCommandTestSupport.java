package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.memberConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;

abstract class CheckCommandTestSupport {
    @TempDir
    protected Path tempDir;

    protected Path createProject(String name) throws IOException {
        Path projectDir = tempDir.resolve(name);
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig(name));
        return projectDir;
    }
}
