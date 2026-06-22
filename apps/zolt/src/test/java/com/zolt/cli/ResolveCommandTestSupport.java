package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.memberConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class ResolveCommandTestSupport {
    private ResolveCommandTestSupport() {}

    static void writeProjectConfig(Path projectDir, Map<String, String> dependencies) throws IOException {
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2", dependencies);
    }

    static void writeProjectConfig(
            Path projectDir,
            String repositoryUrl,
            Map<String, String> dependencies) throws IOException {
        Files.createDirectories(projectDir);
        StringBuilder config = new StringBuilder(memberConfig("demo") + """
                main = "com.example.Main"

                [repositories]
                test = "%s"

                [dependencies]
                """.formatted(repositoryUrl));
        dependencies.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> config.append('"')
                        .append(entry.getKey())
                        .append("\" = \"")
                        .append(entry.getValue())
                        .append("\"\n"));
        config.append("""

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);
        Files.writeString(projectDir.resolve("zolt.toml"), config.toString());
    }

    static String jsonPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
