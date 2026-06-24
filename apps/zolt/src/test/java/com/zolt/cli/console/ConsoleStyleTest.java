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
    void disabledStyleLeavesTextUnchanged() {
        ConsoleStyle style = ConsoleStyle.disabled();

        assertEquals("Basics", style.heading("Basics"));
        assertEquals("resolve", style.command("resolve"));
        assertEquals("error:", style.error("error:"));
    }

    @Test
    void enabledStyleColorsOnlyTheProvidedFragment() {
        ConsoleStyle style = ConsoleStyle.enabled();

        assertEquals("\u001B[1mBasics\u001B[0m", style.heading("Basics"));
        assertEquals("\u001B[36mresolve\u001B[0m", style.command("resolve"));
        assertEquals("\u001B[32mResolved\u001B[0m", style.success("Resolved"));
        assertEquals("\u001B[33mwarning:\u001B[0m", style.warning("warning:"));
        assertEquals("\u001B[31merror:\u001B[0m", style.error("error:"));
        assertEquals("\u001B[2m12ms\u001B[0m", style.muted("12ms"));
    }
}
