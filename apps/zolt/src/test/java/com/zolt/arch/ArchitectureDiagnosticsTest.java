package com.zolt.arch;

import static com.zolt.arch.ArchitectureDiagnostics.describe;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ArchitectureDiagnosticsTest {
    @Test
    void describeFormatsBulletList() {
        assertEquals("- first\n- second\n", describe(List.of("first", "second")));
    }
}
