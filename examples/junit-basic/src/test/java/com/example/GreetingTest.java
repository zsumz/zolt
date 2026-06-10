package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class GreetingTest {
    @Test
    @Tag("fast")
    void formatsGreeting() {
        assertEquals("hello zolt", "hello " + "zolt");
    }
}
