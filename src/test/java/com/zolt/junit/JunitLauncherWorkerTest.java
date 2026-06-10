package com.zolt.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class JunitLauncherWorkerTest {
    @Test
    void missingTestOutputDirectoryIsActionable() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = new JunitLauncherWorker().run(
                new String[0],
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8)
                .contains("JUnit launcher worker requires exactly one test output directory"));
    }
}
