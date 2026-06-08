package com.example.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class HelloResourceTest {
    @Test
    void returnsGreeting() {
        assertEquals("Hello from Quarkus via Zolt!", new HelloResource().hello());
    }
}
