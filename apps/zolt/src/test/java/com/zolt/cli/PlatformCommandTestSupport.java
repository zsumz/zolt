package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.memberConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

abstract class PlatformCommandTestSupport {
    protected static void writeProjectConfig(Path projectDir) throws IOException {
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
    }

    protected static void writeProjectConfig(Path projectDir, String repositoryUrl) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo") + """
                main = "com.example.Main"

                [repositories]
                test = "%s"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """.formatted(repositoryUrl));
    }
}
