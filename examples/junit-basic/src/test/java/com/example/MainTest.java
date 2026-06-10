package com.example;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {
    @Test
    @Tag("fast")
    void addsNumbers() {
        assertEquals(5, Main.add(2, 3));
    }

    @Test
    @Tag("math")
    void addsNegativeNumbers() {
        assertEquals(-5, Main.add(-2, -3));
    }
}
