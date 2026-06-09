package com.example.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class HelloResourceQuarkusTest {
    @Inject
    HelloResource resource;

    @Test
    void injectsApplicationResource() {
        assertEquals("Hello from Quarkus via Zolt!", resource.hello());
    }
}
