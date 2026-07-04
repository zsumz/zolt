package sh.zolt.workspace.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import sh.zolt.workspace.WorkspaceConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class WorkspaceTomlWriterTest {
    private final WorkspaceTomlWriter writer = new WorkspaceTomlWriter();
    private final WorkspaceConfigParser parser = new WorkspaceConfigParser();

    @Test
    void writesMultiMemberArraysOneEntryPerLine() {
        String toml = writer.write(new WorkspaceConfig(
                "acme-platform",
                List.of("apps/api", "modules/core"),
                List.of("apps/api", "modules/core"),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of()));

        assertEquals("""
                [workspace]
                name = "acme-platform"
                members = [
                    "apps/api",
                    "modules/core",
                ]
                defaultMembers = [
                    "apps/api",
                    "modules/core",
                ]

                [repositories]
                "central" = "https://repo.maven.apache.org/maven2"
                """, toml);

        WorkspaceConfig parsed = parser.parse(toml);
        assertEquals(List.of("apps/api", "modules/core"), parsed.members());
        assertEquals(List.of("apps/api", "modules/core"), parsed.defaultMembers());
    }

    @Test
    void keepsSingleMemberArraysCompact() {
        String toml = writer.write(new WorkspaceConfig(
                "app",
                List.of("apps/app"),
                List.of("apps/app"),
                Map.of(),
                Map.of()));

        assertEquals("""
                [workspace]
                name = "app"
                members = ["apps/app"]
                defaultMembers = ["apps/app"]
                """, toml);
    }
}
