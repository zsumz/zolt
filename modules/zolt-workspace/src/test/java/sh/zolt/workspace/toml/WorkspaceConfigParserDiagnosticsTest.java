package sh.zolt.workspace.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.workspace.WorkspaceConfigException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceConfigParserDiagnosticsTest {
    private final WorkspaceConfigParser parser = new WorkspaceConfigParser();

    @TempDir
    private Path tempDir;

    @Test
    void reportsMalformedWorkspaceTomlWithSourceAndPosition() {
        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> parser.parse("""
                        [workspace]
                        name = "bad"
                        members = [
                        """));

        assertTrue(exception.getMessage().contains("Could not parse zolt-workspace.toml."));
        assertTrue(exception.getMessage().contains("Fix the TOML syntax near"));
    }

    @Test
    void rejectsMissingWorkspaceSectionInRootConfig() {
        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> parser.parseRootConfig("""
                        [project]
                        name = "root"
                        version = "0.1.0"
                        group = "com.acme"
                        java = "21"
                        """));

        assertEquals("Missing required section [workspace] in zolt.toml.", exception.getMessage());
    }

    @Test
    void rejectsUnknownWorkspaceFields() {
        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> parser.parse("""
                        [workspace]
                        name = "bad"
                        members = ["apps/api"]
                        member = ["apps/api"]
                        """));

        assertEquals(
                "Unknown field [workspace].member in zolt-workspace.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void rejectsMissingWorkspaceName() {
        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> parser.parse("""
                        [workspace]
                        members = ["apps/api"]
                        """));

        assertEquals(
                "Missing required field [workspace].name in zolt-workspace.toml. Add a non-empty string value.",
                exception.getMessage());
    }

    @Test
    void rejectsEmptyWorkspaceMembers() {
        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> parser.parse("""
                        [workspace]
                        name = "bad"
                        members = []
                        """));

        assertEquals(
                "Invalid value for [workspace].members in zolt-workspace.toml. Add at least one member path.",
                exception.getMessage());
    }

    @Test
    void rejectsNonArrayDefaultMembers() {
        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> parser.parse("""
                        [workspace]
                        name = "bad"
                        members = ["apps/api"]
                        defaultMembers = "apps/api"
                        """));

        assertEquals(
                "Invalid value for [workspace].defaultMembers in zolt-workspace.toml. Use an array of strings.",
                exception.getMessage());
    }

    @Test
    void rejectsBlankDefaultMemberEntries() {
        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> parser.parse("""
                        [workspace]
                        name = "bad"
                        members = ["apps/api"]
                        defaultMembers = ["apps/api", " "]
                        """));

        assertEquals(
                "Invalid value for [workspace].defaultMembers[1] in zolt-workspace.toml. Use a non-empty string.",
                exception.getMessage());
    }

    @Test
    void rejectsDuplicateDefaultMemberEntries() {
        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> parser.parse("""
                        [workspace]
                        name = "bad"
                        members = ["apps/api"]
                        defaultMembers = ["apps/api", "apps/api"]
                        """));

        assertEquals(
                "Duplicate value `apps/api` in [workspace].defaultMembers in zolt-workspace.toml.",
                exception.getMessage());
    }

    @Test
    void rejectsBlankPlatformValues() {
        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> parser.parse("""
                        [workspace]
                        name = "bad"
                        members = ["apps/api"]

                        [platforms]
                        "com.acme:platform" = " "
                        """));

        assertEquals(
                "Invalid value for [platforms].com.acme:platform in zolt-workspace.toml. Use a non-empty string value.",
                exception.getMessage());
    }

    @Test
    void reportsUnreadableWorkspaceConfigPath() {
        Path missing = tempDir.resolve("missing-workspace.toml");

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> parser.parse(missing));

        assertEquals(
                "Could not read zolt-workspace.toml at " + missing + ". Check that the file exists and is readable.",
                exception.getMessage());
    }

    @Test
    void reportsUnreadableRootConfigPath() {
        Path missing = tempDir.resolve("missing-root-zolt.toml");

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> parser.parseRootConfig(missing));

        assertEquals(
                "Could not read zolt.toml at " + missing + ". Check that the file exists and is readable.",
                exception.getMessage());
    }

    @Test
    void reportsUnreadableRootConfigPathWhileCheckingForWorkspaceSection() {
        Path missing = tempDir.resolve("missing-zolt.toml");

        WorkspaceConfigException exception = assertThrows(
                WorkspaceConfigException.class,
                () -> parser.hasWorkspaceSection(missing));

        assertEquals(
                "Could not read zolt.toml at " + missing + ". Check that the file exists and is readable.",
                exception.getMessage());
    }
}
