package sh.zolt.selfhost;

import sh.zolt.build.BuildResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class SelfCheckServiceTestSupport {
    private SelfCheckServiceTestSupport() {
    }

    static BuildResult buildResult(Path projectDirectory, int sourceCount) {
        return new BuildResult(
                Optional.empty(),
                sourceCount,
                0,
                projectDirectory.resolve("target/classes"),
                "");
    }

    static void writeSelfHostingProject(Path tempDir, boolean includeTestRunner) throws IOException {
        Files.writeString(tempDir.resolve("zolt.lock"), "version = 1\n");
        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.createDirectories(tempDir.resolve("src/test/java"));
        Files.writeString(tempDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                main = "com.example.Main"

                [repositories]
                central = "https://repo.maven.apache.org/maven2"

                %s
                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"

                [native]
                imageName = "demo"
                output = "target/native"
                args = ["--no-fallback"]
                """.formatted(includeTestRunner
                ? """
                [test.dependencies]
                "org.junit.platform:junit-platform-console-standalone" = "1.11.4"

                """
                : ""));
    }
}
