package com.zolt.quarkus.annotation.diagnostic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class QuarkusBuildChainDiagnosticTest {
    @Test
    void writesSystemBuildChainDiagnostic() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusBuildChainDiagnostic diagnostic = diagnostic(out);

        diagnostic.write(null);

        String output = output(out);
        assertTrue(output.contains("Zolt Quarkus build-chain diagnostic:"));
        assertTrue(output.contains("  systemLoader="));
        assertTrue(output.contains("    TestBuildChainFunction=<unavailable: ClassNotFoundException>"));
        assertFalse(output.contains("  quarkusRuntimeLoader="));
    }

    @Test
    void skipsRuntimeDiagnosticWhenRuntimeLoaderMatchesSystemLoader() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusBuildChainDiagnostic diagnostic = diagnostic(out);

        diagnostic.write(ClassLoader.getSystemClassLoader());

        assertFalse(output(out).contains("  quarkusRuntimeLoader="));
    }

    @Test
    void writesRuntimeDiagnosticWhenRuntimeLoaderDiffers() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusBuildChainDiagnostic diagnostic = diagnostic(out);
        ClassLoader runtimeLoader = new ClassLoader(ClassLoader.getSystemClassLoader()) {
        };

        diagnostic.write(runtimeLoader);

        String output = output(out);
        assertTrue(output.contains("  systemLoader="));
        assertTrue(output.contains("  quarkusRuntimeLoader="));
    }

    private static QuarkusBuildChainDiagnostic diagnostic(ByteArrayOutputStream out) {
        return new QuarkusBuildChainDiagnostic(new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    private static String output(ByteArrayOutputStream out) {
        return out.toString(StandardCharsets.UTF_8);
    }
}
