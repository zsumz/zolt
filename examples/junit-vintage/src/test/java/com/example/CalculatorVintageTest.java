package com.example;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class CalculatorVintageTest {
    @Test
    public void addsNumbers() {
        assertEquals(5, new Calculator().add(2, 3));
    }
}
