package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

final class ModernConsoleOutputDocumentationTest {
    @Test
    void modernConsoleOutputDesignNamesColorModesCommandFamiliesAndNonGoals() throws IOException {
        String design = Files.readString(RepositoryPaths.root().resolve("docs/modern-console-output.md"));

        assertTrue(design.contains("sharp infrastructure tool"));
        assertTrue(design.contains("Cargo is a useful reference"));
        assertTrue(design.contains("uv is also useful inspiration for the control surface around output"));
        assertTrue(design.contains("color and formatting are additive"));
        assertTrue(design.contains("--color=auto|always|never"));
        assertTrue(design.contains("Progress"));
        assertTrue(design.contains("`--quiet` for stable low-noise runs"));
        assertTrue(design.contains("`progress-output.md` defines"));
        assertTrue(design.contains("NO_COLOR"));
        assertTrue(design.contains("Machine-readable formats such as `--format json` ignore color"));
        assertTrue(design.contains("Zolt keeps grouped root commands"));
        assertTrue(design.contains("help section headings are bold green"));
        assertTrue(design.contains("help command and option tokens are bold cyan"));
        assertTrue(design.contains("usage metavars, optional brackets, and ellipses are cyan"));
        assertTrue(design.contains("Subcommand help should also be grouped by purpose."));
        assertTrue(design.contains("Workspace Selection:"));
        assertTrue(design.contains("Resolve output should answer"));
        assertTrue(design.contains("Build output should keep compile, resources, generated sources, and skipped work visible"));
        assertTrue(design.contains("Quality checks benefit from a compact status table"));
        assertTrue(design.contains("Use short blocks for failures"));
        assertTrue(design.contains("Implemented foundation:"));
        assertTrue(design.contains("Cargo-style help guardrails."));
        assertTrue(design.contains("A command-tree inventory keeps every registered command path assigned"));
        assertTrue(design.contains("Remaining rollout:"));
        assertTrue(design.contains("Refresh common success summaries."));
        assertTrue(design.contains("Standardize error blocks."));
        assertTrue(design.contains("color-enabled golden test for help, success, and error"));
        assertTrue(design.contains("No full-screen terminal UI"));
        assertTrue(design.contains("No progress bars, spinners, or carriage-return status lines before"));
    }

    @Test
    void docsIndexLinksModernConsoleOutputDesign() throws IOException {
        String docsIndex = Files.readString(RepositoryPaths.root().resolve("docs/README.md"));
        String consoleOutput = Files.readString(RepositoryPaths.root().resolve("docs/console-output.md"));
        String modernConsoleOutput = Files.readString(RepositoryPaths.root().resolve("docs/modern-console-output.md"));
        String followUpIndex = Files.readString(RepositoryPaths.root().resolve("followUps/README.md"));

        assertTrue(docsIndex.contains("`modern-console-output.md`"));
        assertTrue(docsIndex.contains("`progress-output.md`"));
        assertTrue(consoleOutput.contains("`modern-console-output.md` defines the human output design"));
        assertTrue(consoleOutput.contains("Zolt supports:"));
        assertTrue(consoleOutput.contains("Default mode is `auto` for human-facing commands."));
        assertTrue(consoleOutput.contains("Focused help-surface tests also guard Cargo-style whitespace"));
        assertTrue(consoleOutput.contains("`progress-output.md` defines"));
        assertTrue(modernConsoleOutput.contains("`progress-output.md` defines"));
        assertTrue(followUpIndex.contains("**M29** — Modern console output"));
    }

    @Test
    void progressOutputDesignNamesControlsContractsAndFollowUpFollowUps() throws IOException {
        String design = Files.readString(RepositoryPaths.root().resolve("docs/progress-output.md"));
        String milestones = Files.readString(RepositoryPaths.root().resolve("followUps/MILESTONES.md"));

        assertTrue(design.contains("--progress=auto|always|never"));
        assertTrue(design.contains("--no-progress"));
        assertTrue(design.contains("Progress writes to stderr."));
        assertTrue(design.contains("`--progress=never` suppresses progress."));
        assertTrue(design.contains("`--progress=auto` disables progress when stderr is redirected."));
        assertTrue(design.contains("Machine-readable or parseable outputs ignore `--progress=auto`"));
        assertTrue(design.contains("CI indicators such as `CI`, `WOODPECKER`,"));
        assertTrue(design.contains("`--color=never --progress=always` emits plain progress lines."));
        assertTrue(design.contains("`NO_COLOR` affects color only."));
        assertTrue(design.contains("no spinners, bars, or carriage-return rewrites"));
        assertTrue(design.contains("Core resolver, planner, and build services should"));
        assertTrue(design.contains("Native Image smoke logs do not depend on terminal animation support."));
        assertTrue(milestones.contains(" — Add global progress mode"));
        assertTrue(milestones.contains(" — Add progress writer and command progress events"));
        assertTrue(milestones.contains(" — Add global quiet mode"));
    }
}
