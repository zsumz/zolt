package com.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {
    @Test
    void addsNumbers() {
        assertEquals(5, Main.add(2, 3));
    }
}
