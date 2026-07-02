package com.zolt.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.error.ActionableException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ProjectVersionOverrideTest {
    @Test
    void blankValueResolvesToEmptyOverride() {
        assertEquals(Optional.empty(), ProjectVersionOverride.fromValue(null));
        assertEquals(Optional.empty(), ProjectVersionOverride.fromValue(""));
        assertEquals(Optional.empty(), ProjectVersionOverride.fromValue("  "));
    }

    @Test
    void acceptsBaseNightlyAndZapShapes() {
        assertEquals(Optional.of("0.1.0"), ProjectVersionOverride.fromValue("0.1.0"));
        assertEquals(Optional.of("0.1.0-SNAPSHOT"), ProjectVersionOverride.fromValue("0.1.0-SNAPSHOT"));
        assertEquals(
                Optional.of("0.1.0-nightly.20260628.0123456789ab"),
                ProjectVersionOverride.fromValue(" 0.1.0-nightly.20260628.0123456789ab "));
        assertEquals(
                Optional.of("0.1.0-zap.20260702.0123456789ab"),
                ProjectVersionOverride.fromValue(" 0.1.0-zap.20260702.0123456789ab "));
    }

    @Test
    void rejectsMalformedOverrideWithActionableRemediation() {
        ActionableException notAVersion = assertThrows(
                ActionableException.class, () -> ProjectVersionOverride.fromValue("not a version"));
        assertTrue(notAVersion.error().summary().contains("not a version"));
        assertTrue(notAVersion.error().remediation().contains("ZOLT_VERSION_OVERRIDE"));
        assertTrue(notAVersion.error().remediation().contains("0.1.0-nightly"));
        assertTrue(notAVersion.error().remediation().contains("0.1.0-zap"));

        // Whitespace inside the value is rejected.
        assertThrows(ActionableException.class, () -> ProjectVersionOverride.fromValue("0.1.0 nightly"));
        // Not a three-part base version and not a nightly string.
        assertThrows(ActionableException.class, () -> ProjectVersionOverride.fromValue("1.2"));
        // Empty pre-release segment after the dash is rejected by the grammar.
        assertThrows(ActionableException.class, () -> ProjectVersionOverride.fromValue("0.1.0-"));
    }

    @Test
    void resolveVersionFallsBackToCompiledDefault() {
        assertEquals("0.1.0-SNAPSHOT", ProjectVersionOverride.resolveVersion("0.1.0-SNAPSHOT"));
    }

    @Test
    void withVersionReplacesOnlyTheVersionField() {
        ProjectConfig base = config();
        ProjectConfig updated = base.withVersion("0.1.0-nightly.20260628.0123456789ab");

        assertEquals("0.1.0-nightly.20260628.0123456789ab", updated.project().version());
        assertEquals(base.project().name(), updated.project().name());
        assertEquals(base.project().group(), updated.project().group());
        assertEquals(base.project().java(), updated.project().java());
        assertEquals(base.project().main(), updated.project().main());
        assertEquals(base.dependencies(), updated.dependencies());
        assertEquals(base.repositories(), updated.repositories());
    }

    @Test
    void applyLeavesConfigUnchangedWhenOverrideUnset() {
        // ZOLT_VERSION_OVERRIDE is not set in the test JVM, so apply() is a no-op pass-through.
        ProjectConfig base = config();
        assertSame(base, ProjectVersionOverride.apply(base));
    }

    private static ProjectConfig config() {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0-SNAPSHOT", "com.example", "21", Optional.of("com.example.Main")),
                ProjectConfig.defaultRepositories(),
                Map.of("com.example:main", "1.0.0"),
                Map.of("com.example:test", "1.0.0"),
                BuildSettings.defaults(),
                NativeSettings.defaults());
    }
}
