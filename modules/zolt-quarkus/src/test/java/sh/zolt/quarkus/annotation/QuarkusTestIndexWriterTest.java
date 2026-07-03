package sh.zolt.quarkus.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import sh.zolt.quarkus.testsupport.QuarkusJandexFixtureCompiler;
import sh.zolt.quarkus.testsupport.QuarkusJandexFixtureCompiler.FixtureRuntime;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusTestIndexWriterTest {
    @Test
    void enrichesQuarkusTestClassWithoutExistingExtendWith(@TempDir Path tempDir) throws Exception {
        Path testOutputDirectory = QuarkusJandexFixtureCompiler.compile(
                tempDir.resolve("test-classes"),
                Map.of("com.example.HttpQuarkusCase", """
                        package com.example;

                        @io.quarkus.test.junit.QuarkusTest
                        public class HttpQuarkusCase {
                        }
                        """));

        try (FixtureRuntime runtime = QuarkusJandexFixtureCompiler.fixtureRuntime()) {
            Object writer = runtime.newInstance("sh.zolt.quarkus.annotation.QuarkusTestIndexWriter");
            Object rawIndex = invokeWriter(writer, "createRawIndex", testOutputDirectory);
            Object enrichedIndex = invokeWriter(
                    writer,
                    "createIndex",
                    testOutputDirectory,
                    List.of("com.example.HttpQuarkusCase"));

            assertEquals(0, annotationCount(runtime, rawIndex, "org.junit.jupiter.api.extension.ExtendWith"));
            assertEquals(1, annotationCount(runtime, enrichedIndex, "org.junit.jupiter.api.extension.ExtendWith"));
        }
    }

    @Test
    void doesNotDuplicateExistingExtendWithCandidate(@TempDir Path tempDir) throws Exception {
        Path testOutputDirectory = QuarkusJandexFixtureCompiler.compile(
                tempDir.resolve("test-classes"),
                Map.of("com.example.ProfiledQuarkusCase", """
                        package com.example;

                        @io.quarkus.test.junit.QuarkusTest
                        @org.junit.jupiter.api.extension.ExtendWith(io.quarkus.test.junit.QuarkusTestExtension.class)
                        public class ProfiledQuarkusCase {
                        }
                        """));

        try (FixtureRuntime runtime = QuarkusJandexFixtureCompiler.fixtureRuntime()) {
            Object writer = runtime.newInstance("sh.zolt.quarkus.annotation.QuarkusTestIndexWriter");
            Object enrichedIndex = invokeWriter(
                    writer,
                    "createIndex",
                    testOutputDirectory,
                    List.of("com.example.ProfiledQuarkusCase"));

            assertEquals(1, annotationCount(runtime, enrichedIndex, "org.junit.jupiter.api.extension.ExtendWith"));
        }
    }

    private static Object invokeWriter(Object writer, String methodName, Object... arguments) throws Exception {
        Class<?>[] argumentTypes = java.util.Arrays.stream(arguments)
                .map(argument -> {
                    if (argument instanceof Path) {
                        return Path.class;
                    }
                    if (argument instanceof List<?>) {
                        return List.class;
                    }
                    return argument.getClass();
                })
                .toArray(Class<?>[]::new);
        Method method = writer.getClass().getDeclaredMethod(methodName, argumentTypes);
        method.setAccessible(true);
        return method.invoke(writer, arguments);
    }

    private static int annotationCount(FixtureRuntime runtime, Object index, String annotationName) throws Exception {
        Class<?> dotNameClass = runtime.loadClass("org.jboss.jandex.DotName");
        Method createSimple = dotNameClass.getMethod("createSimple", String.class);
        Object dotName = createSimple.invoke(null, annotationName);
        Collection<?> annotations = (Collection<?>) index.getClass()
                .getMethod("getAnnotations", dotNameClass)
                .invoke(index, dotName);
        return annotations.size();
    }
}
