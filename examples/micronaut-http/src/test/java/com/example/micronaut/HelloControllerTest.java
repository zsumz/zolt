package com.example.micronaut;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class HelloControllerTest {
    @Test
    void returnsGreeting() {
        assertEquals("Hello from Micronaut via Zolt!", new HelloController().index());
    }
}
