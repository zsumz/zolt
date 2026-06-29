package com.zolt.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;

final class WorkspaceDiscoveryServiceTestSupport {
    private WorkspaceDiscoveryServiceTestSupport() {}

    static void workspace(Path tempDir, String content) throws IOException {
        Files.writeString(tempDir.resolve("zolt-workspace.toml"), content);
    }

    static void rootWorkspace(Path tempDir, String content) throws IOException {
        Files.writeString(tempDir.resolve("zolt.toml"), content);
    }

    static void member(Path tempDir, String path, String name, String group, String extraToml) throws IOException {
        Path member = tempDir.resolve(path);
        Files.createDirectories(member);
        Files.writeString(member.resolve("zolt.toml"), """
                [project]
                name = "%s"
                version = "0.1.0"
                group = "%s"
                java = "21"
                %s""".formatted(name, group, extraToml));
    }

    static void createSymlink(Path link, Path target) throws IOException {
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException exception) {
            Assumptions.assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
        }
    }
}
