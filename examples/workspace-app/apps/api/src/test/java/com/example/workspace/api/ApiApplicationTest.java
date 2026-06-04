package com.example.workspace.api;

import com.example.workspace.core.Greeting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiApplicationTest {
    @Test
    void usesWorkspaceCoreGreeting() {
        assertEquals("Hello, test, from workspace core!", Greeting.message("test"));
    }
}
