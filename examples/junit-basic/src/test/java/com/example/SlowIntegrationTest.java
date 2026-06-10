package com.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class SlowIntegrationTest {
    @Test
    @Tag("slow")
    void simulatesSlowPath() {
        assertTrue(Main.add(1, 1) == 2);
    }
}
