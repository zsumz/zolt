package sh.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ToolchainRequirementReaderTest {
    @TempDir
    private Path tempDir;

    private final ToolchainRequirementReader reader = new ToolchainRequirementReader();

    @Test
    void findsNearestParentZoltToolchainVersion() throws IOException {
        Path root = tempDir.resolve("workspace");
        Path member = root.resolve("apps/api");
        Files.createDirectories(member);
        Files.writeString(root.resolve("zolt.toml"), """
                [workspace]
                name = "demo"
                members = ["apps/api"]

                [toolchain.zolt]
                version = "0.2.0"
                """);
        Files.writeString(member.resolve("zolt.toml"), """
                [project]
                name = "api"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);

        ToolchainRequirement requirement = reader.find(member.resolve("src/main/java")).orElseThrow();

        assertEquals("0.2.0", requirement.zoltVersion());
        assertEquals(root.resolve("zolt.toml").toAbsolutePath().normalize(), requirement.configPath());
    }

    @Test
    void nearerProjectToolchainVersionOverridesWorkspaceVersion() throws IOException {
        Path root = tempDir.resolve("workspace");
        Path member = root.resolve("apps/api");
        Files.createDirectories(member);
        Files.writeString(root.resolve("zolt.toml"), """
                [workspace]
                name = "demo"
                members = ["apps/api"]

                [toolchain.zolt]
                version = "0.2.0"
                """);
        Files.writeString(member.resolve("zolt.toml"), """
                [project]
                name = "api"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [toolchain.zolt]
                version = "0.3.0"
                """);

        ToolchainRequirement requirement = reader.find(member).orElseThrow();

        assertEquals("0.3.0", requirement.zoltVersion());
        assertEquals(member.resolve("zolt.toml").toAbsolutePath().normalize(), requirement.configPath());
    }

    @Test
    void ignoresConfigsWithoutZoltToolchainVersion() throws IOException {
        Path project = tempDir.resolve("project");
        Files.createDirectories(project);
        Files.writeString(project.resolve("zolt.toml"), """
                [project]
                name = "api"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);

        assertTrue(reader.find(project).isEmpty());
    }
}
