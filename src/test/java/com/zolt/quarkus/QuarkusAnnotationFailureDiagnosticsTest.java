package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class QuarkusAnnotationFailureDiagnosticsTest {
    private final QuarkusAnnotationFailureDiagnostics diagnostics = new QuarkusAnnotationFailureDiagnostics(
            new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

    @Test
    void findsMissingClassFromNestedCausesAndSuppressedFailures() {
        RuntimeException causeWrapper = new RuntimeException(
                "wrapper",
                new NoClassDefFoundError("com/example/Missing"));
        RuntimeException suppressedWrapper = new RuntimeException("suppressed wrapper");
        suppressedWrapper.addSuppressed(new ClassNotFoundException("com.example.Suppressed"));

        assertEquals("com.example.Missing", diagnostics.failingClass(causeWrapper).orElseThrow());
        assertEquals("com.example.Suppressed", diagnostics.failingClass(suppressedWrapper).orElseThrow());
    }

    @Test
    void findsGeneratedInvokerClassFromNestedStackTraces() {
        RuntimeException generatedInvokerFailure = new RuntimeException("generated invoker");
        generatedInvokerFailure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(
                        "com.example.GreetingResource$quarkusrestinvoker$hello_123",
                        "invoke",
                        "GreetingResource.java",
                        12)
        });
        RuntimeException wrapper = new RuntimeException("wrapper", generatedInvokerFailure);

        assertEquals(
                "com.example.GreetingResource$quarkusrestinvoker$hello_123",
                diagnostics.generatedInvokerClass(wrapper).orElseThrow());
        assertTrue(diagnostics.generatedInvokerClass(new RuntimeException("plain")).isEmpty());
    }
}
