package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.project.TestRuntimeSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestRuntimeInputBuilderTest {
    @TempDir
    private Path projectDir;

    private final TestRuntimeInputBuilder builder = new TestRuntimeInputBuilder();

    @Test
    void expandsConfiguredRuntimeInputsAndAppendsCliJvmArguments() {
        TestRuntimeInputs inputs = builder.build(
                projectDir,
                new TestRuntimeSettings(
                        List.of("-Dconfigured=true"),
                        Map.of("logs.dir", "${project.root}/test-logs"),
                        Map.of("APP_HOME", "${project.root}", "TZ", "America/Chicago"),
                        List.of("failed")),
                new TestJvmArguments(List.of("-Dcli=true")),
                List.of("skipped", "failed"));

        Path root = projectDir.toAbsolutePath().normalize();
        assertEquals(List.of(
                "-Dconfigured=true",
                "-Dlogs.dir=" + root.resolve("test-logs"),
                "-Dcli=true"), inputs.jvmArguments().values());
        assertEquals(Map.of(
                "APP_HOME", root.toString(),
                "TZ", "America/Chicago"), inputs.environment());
        assertEquals(List.of("failed", "skipped"), inputs.events());
    }

    @Test
    void rejectsUnsupportedPlaceholdersWithSectionContext() {
        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> builder.build(
                        projectDir,
                        new TestRuntimeSettings(
                                List.of("${unknown}"),
                                Map.of(),
                                Map.of(),
                                List.of()),
                        TestJvmArguments.empty(),
                        List.of()));

        assertEquals(
                "Unsupported placeholder in [test.runtime.jvmArgs] value `${unknown}`. Supported placeholder: ${project.root}.",
                exception.getMessage());
    }

    @Test
    void rejectsUnsupportedCliEvents() {
        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> builder.build(
                        projectDir,
                        TestRuntimeSettings.defaults(),
                        TestJvmArguments.empty(),
                        List.of("verbose")));

        assertEquals(
                "Unsupported test runtime event `verbose`. Supported test runtime events are: passed, skipped, failed.",
                exception.getMessage());
    }

    @Test
    void outputCollectionsAreImmutable() {
        TestRuntimeInputs inputs = builder.build(
                projectDir,
                TestRuntimeSettings.defaults(),
                TestJvmArguments.empty(),
                List.of("failed"));

        assertThrows(UnsupportedOperationException.class, () -> inputs.environment().put("TZ", "UTC"));
        assertThrows(UnsupportedOperationException.class, () -> inputs.events().add("passed"));
    }
}
