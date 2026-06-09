package com.example.large.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class LargeApplicationTest {
    @Test
    void messageIncludesServiceOutput() {
        assertTrue(LargeApplication.message().contains("Large-workspace"));
    }
}
