package com.zolt.quarkus.annotation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusResourceElementDiagnosticTest {
    @Test
    void reportsQuarkusResourceElementsWhenLoaderExposesThem() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusResourceElementDiagnostic diagnostic = diagnostic(out);
        FakeResourceClassLoader classLoader = new FakeResourceClassLoader(List.of(
                new FakeClassPathElement(Path.of("target", "quarkus-app", "quarkus", "generated-bytecode.jar"))));

        diagnostic.write(
                "generatedInvoker.actualLoader",
                "com.example.HelloResource$quarkusrestinvoker$hello",
                classLoader);

        String output = output(out);
        assertTrue(output.contains(
                "generatedInvoker.actualLoader.resource=com/example/HelloResource$quarkusrestinvoker$hello.class"));
        assertTrue(output.contains(
                "generatedInvoker.actualLoader.resourceElement.0=target/quarkus-app/quarkus/generated-bytecode.jar"));
    }

    @Test
    void reportsUnavailableResourceElementsForPlainClassLoaders() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        QuarkusResourceElementDiagnostic diagnostic = diagnostic(out);

        diagnostic.write(
                "generatedInvoker.actualLoader",
                "com.example.HelloResource",
                QuarkusResourceElementDiagnosticTest.class.getClassLoader());

        assertTrue(output(out).contains(
                "generatedInvoker.actualLoader.resourceElements=<unavailable: NoSuchMethodException>"));
    }

    private static QuarkusResourceElementDiagnostic diagnostic(ByteArrayOutputStream out) {
        return new QuarkusResourceElementDiagnostic(new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    private static String output(ByteArrayOutputStream out) {
        return out.toString(StandardCharsets.UTF_8);
    }

    static final class FakeResourceClassLoader extends ClassLoader {
        private final List<FakeClassPathElement> elements;

        private FakeResourceClassLoader(List<FakeClassPathElement> elements) {
            super(QuarkusResourceElementDiagnosticTest.class.getClassLoader());
            this.elements = elements;
        }

        public List<FakeClassPathElement> getElementsWithResource(String resourceName, boolean includeParent) {
            return elements;
        }
    }

    static final class FakeClassPathElement {
        private final Path root;

        private FakeClassPathElement(Path root) {
            this.root = root;
        }

        public Path getRoot() {
            return root;
        }
    }
}
