package com.zolt.command.toml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.command.CommandConfig;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CommandConfigParserTest {
    private static final Set<String> BUILT_INS = Set.of(
            "build",
            "check",
            "package",
            "resolve",
            "run",
            "task",
            "test");

    private final CommandConfigParser parser = new CommandConfigParser(BUILT_INS);

    @Test
    void parsesAliasesAndTasksFromRootCommandConfig() {
        CommandConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [commands.aliases]
                b = ["build"]
                ci = ["check", "--context", "ci"]

                [commands.tasks.fmt]
                description = "Format Java sources"
                cmd = ["scripts/format"]

                [commands.tasks.docs]
                description = "Serve docs"
                cmd = ["python3", "-m", "http.server", "8000"]
                cwd = "docs"
                env = { APP_ENV = "local", EMPTY = "" }
                """);

        assertEquals(List.of("b", "ci"), List.copyOf(config.aliases().keySet()));
        assertEquals(List.of("build"), config.aliases().get("b").argv());
        assertEquals(List.of("check", "--context", "ci"), config.aliases().get("ci").argv());
        assertEquals(List.of("fmt", "docs"), List.copyOf(config.tasks().keySet()));
        assertEquals("Format Java sources", config.tasks().get("fmt").description().orElseThrow());
        assertEquals(List.of("scripts/format"), config.tasks().get("fmt").cmd());
        assertEquals("docs", config.tasks().get("docs").cwd().orElseThrow());
        assertEquals(Map.of("APP_ENV", "local", "EMPTY", ""), config.tasks().get("docs").env());
    }

    @Test
    void returnsEmptyConfigWhenCommandsSectionIsMissing() {
        CommandConfig config = parser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);

        assertTrue(config.aliases().isEmpty());
        assertTrue(config.tasks().isEmpty());
    }

    @Test
    void rejectsInvalidAliasNames() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [commands.aliases]
                "-bad" = ["build"]
                """));

        assertEquals(
                "Invalid [commands.aliases] alias `-bad` in zolt.toml. Alias names may contain only letters, digits, dot, underscore, and hyphen, and must not start with hyphen.",
                exception.getMessage());
    }

    @Test
    void rejectsEmptyAliasArgvEntries() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [commands.aliases]
                ci = ["check", ""]
                """));

        assertEquals(
                "Invalid value for [commands.aliases].ci[1] in zolt.toml. Use a non-empty string.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownTaskFields() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [commands.tasks.fmt]
                cmd = ["scripts/format"]
                shell = "scripts/format"
                """));

        assertEquals(
                "Unknown field [commands.tasks.fmt].shell in zolt.toml. Remove it or check the spelling.",
                exception.getMessage());
    }

    @Test
    void rejectsMalformedTaskEnvValues() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [commands.tasks.db]
                cmd = ["scripts/db-migrate"]
                env = { APP_ENV = 1 }
                """));

        assertEquals(
                "Invalid value for [commands.tasks.db.env].APP_ENV in zolt.toml. Use a literal string value.",
                exception.getMessage());
    }

    @Test
    void rejectsAliasBuiltInShadowing() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [commands.aliases]
                build = ["test"]
                """));

        assertEquals(
                "Invalid [commands.aliases] alias `build` in zolt.toml. Aliases cannot replace built-in Zolt commands.",
                exception.getMessage());
    }

    @Test
    void rejectsTaskBuiltInShadowing() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [commands.tasks.test]
                cmd = ["scripts/test"]
                """));

        assertEquals(
                "Invalid [commands.tasks] task `test` in zolt.toml. Tasks cannot replace built-in Zolt commands.",
                exception.getMessage());
    }

    @Test
    void rejectsAliasesThatTargetTasks() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [commands.aliases]
                fmt = ["task", "fmt"]
                """));

        assertEquals(
                "Invalid [commands.aliases].fmt in zolt.toml. Alias targets cannot be `task`; tasks must be run explicitly with `zolt task <name>`.",
                exception.getMessage());
    }

    @Test
    void rejectsAliasesThatShareTaskNames() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [commands.aliases]
                fmt = ["check"]

                [commands.tasks.fmt]
                cmd = ["scripts/format"]
                """));

        assertEquals(
                "Invalid [commands.aliases] alias `fmt` in zolt.toml. Alias names must not equal task names; run tasks explicitly with `zolt task fmt`.",
                exception.getMessage());
    }

    @Test
    void rejectsShellStringAliases() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [commands.aliases]
                ci = ["check && test"]
                """));

        assertEquals(
                "Invalid [commands.aliases].ci[0] in zolt.toml. Aliases cannot use shell strings; put external commands in [commands.tasks].",
                exception.getMessage());
    }

    @Test
    void rejectsExternalExecutableAliasTargets() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [commands.aliases]
                ci = ["./scripts/check"]
                """));

        assertEquals(
                "Invalid [commands.aliases].ci in zolt.toml. Alias target `./scripts/check` looks like an executable path; external programs belong in [commands.tasks].",
                exception.getMessage());
    }

    @Test
    void rejectsEnvironmentAssignmentAliasPrefixes() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [commands.aliases]
                ci = ["APP_ENV=ci", "check"]
                """));

        assertEquals(
                "Invalid [commands.aliases].ci[0] in zolt.toml. Aliases cannot include environment assignment prefixes; put external commands in [commands.tasks].",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownAliasTargets() {
        ZoltConfigException exception = assertThrows(ZoltConfigException.class, () -> parser.parse("""
                [commands.aliases]
                ci = ["verify"]
                """));

        assertEquals(
                "Invalid [commands.aliases].ci in zolt.toml. Alias target `verify` is not a built-in Zolt command.",
                exception.getMessage());
    }

    @Test
    void commandConveniencesDoNotChangeProjectConfig() {
        ZoltTomlParser projectParser = new ZoltTomlParser();
        ProjectConfig withoutCommands = projectParser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [dependencies]
                "com.google.guava:guava" = "33.4.0-jre"
                """);
        ProjectConfig withCommands = projectParser.parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [dependencies]
                "com.google.guava:guava" = "33.4.0-jre"

                [commands.aliases]
                b = ["build"]

                [commands.tasks.fmt]
                cmd = ["scripts/format"]
                """);

        assertEquals(withoutCommands, withCommands);
    }
}
