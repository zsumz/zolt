package com.example.adoption;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class MainTest {
    @Test
    void readsResourceAndUsesExternalDependencies() {
        assertEquals("Hello from adoption!", Main.greeting("adoption"));
    }
}
