package com.zolt.cli.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class ConsoleStyleTest {
    @Test
    void colorModeHonorsAlwaysNeverAutoAndNoColor() {
        assertTrue(ColorMode.ALWAYS.enabled(false, Map.of("NO_COLOR", "1")));
        assertFalse(ColorMode.NEVER.enabled(true, Map.of()));
        assertTrue(ColorMode.AUTO.enabled(true, Map.of()));
        assertFalse(ColorMode.AUTO.enabled(false, Map.of()));
        assertFalse(ColorMode.AUTO.enabled(true, Map.of("NO_COLOR", "1")));
    }

    @Test
    void progressPolicyHonorsModesCiEnvironmentAndOutputContracts() {
        assertTrue(ProgressPolicy.of(ProgressMode.ALWAYS, false, false, Map.of("CI", "true")).enabledForHumanOutput());
        assertFalse(ProgressPolicy.of(ProgressMode.NEVER, false, true, Map.of()).enabledForHumanOutput());
        assertTrue(ProgressPolicy.of(ProgressMode.AUTO, false, true, Map.of()).enabledForHumanOutput());
        assertFalse(ProgressPolicy.of(ProgressMode.AUTO, false, false, Map.of()).enabledForHumanOutput());
        assertFalse(ProgressPolicy.of(ProgressMode.ALWAYS, true, true, Map.of()).enabledForHumanOutput());
        assertFalse(ProgressPolicy.of(ProgressMode.ALWAYS, false, true, Map.of()).enabledForParseableOutput());
        assertTrue(ProgressPolicy.of(ProgressMode.ALWAYS, false, true, Map.of("NO_COLOR", "1")).enabledForHumanOutput());
    }

    @Test
    void progressAutoHonorsCiEnvironmentFlags() {
        Map.of(
                        "CI", "true",
                        "WOODPECKER", "1",
                        "GITHUB_ACTIONS", "true",
                        "BUILDKITE", "yes")
                .forEach((key, value) -> assertFalse(
                        ProgressPolicy.of(ProgressMode.AUTO, false, true, Map.of(key, value)).enabledForHumanOutput()));

        for (String value : new String[] {"0", "false", "no", " "}) {
            assertTrue(ProgressPolicy.of(ProgressMode.AUTO, false, true, Map.of("CI", value)).enabledForHumanOutput());
        }
    }

    @Test
    void disabledStyleLeavesTextUnchanged() {
        ConsoleStyle style = ConsoleStyle.disabled();

        assertEquals("Basics", style.heading("Basics"));
        assertEquals("Usage:", style.helpHeading("Usage:"));
        assertEquals("resolve", style.command("resolve"));
        assertEquals("zolt test", style.helpCommand("zolt test"));
        assertEquals("[COMMAND]", style.helpMeta("[COMMAND]"));
        assertEquals("target/classes", style.path("target/classes"));
        assertEquals("Resolved", style.success("Resolved"));
        assertEquals("--workspace", style.option("--workspace"));
        assertEquals("Building", style.work("Building"));
        assertEquals("warning:", style.warning("warning:"));
        assertEquals("error:", style.error("error:"));
        assertEquals("12ms", style.muted("12ms"));
    }

    @Test
    void enabledStyleColorsOnlyTheProvidedFragment() {
        ConsoleStyle style = ConsoleStyle.enabled();

        assertEquals("\u001B[1mBasics\u001B[0m", style.heading("Basics"));
        assertEquals("\u001B[1;32mUsage:\u001B[0m", style.helpHeading("Usage:"));
        assertEquals("\u001B[36mresolve\u001B[0m", style.command("resolve"));
        assertEquals("\u001B[1;36mzolt test\u001B[0m", style.helpCommand("zolt test"));
        assertEquals("\u001B[36m[COMMAND]\u001B[0m", style.helpMeta("[COMMAND]"));
        assertEquals("\u001B[36mtarget/classes\u001B[0m", style.path("target/classes"));
        assertEquals("\u001B[32mResolved\u001B[0m", style.success("Resolved"));
        assertEquals("\u001B[1;36m--workspace\u001B[0m", style.option("--workspace"));
        assertEquals("\u001B[36mBuilding\u001B[0m", style.work("Building"));
        assertEquals("\u001B[33mwarning:\u001B[0m", style.warning("warning:"));
        assertEquals("\u001B[31merror:\u001B[0m", style.error("error:"));
        assertEquals("\u001B[2m12ms\u001B[0m", style.muted("12ms"));
    }

    @Test
    void enabledStyleLeavesEmptyFragmentsPlain() {
        ConsoleStyle style = ConsoleStyle.enabled();

        assertEquals("", style.heading(""));
        assertEquals("", style.helpHeading(""));
        assertEquals("", style.command(""));
        assertEquals("", style.helpCommand(""));
        assertEquals("", style.helpMeta(""));
        assertEquals("", style.path(""));
        assertEquals("", style.success(""));
        assertEquals("", style.option(""));
        assertEquals("", style.work(""));
        assertEquals("", style.warning(""));
        assertEquals("", style.error(""));
        assertEquals("", style.muted(""));
    }
}
