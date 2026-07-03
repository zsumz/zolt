package sh.zolt.quarkus.annotation.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import sh.zolt.quarkus.testsupport.QuarkusJandexFixtureCompiler;
import sh.zolt.quarkus.testsupport.QuarkusJandexFixtureCompiler.FixtureRuntime;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusSelectedTestIndexDiagnosticTest {
    @Test
    void formatsProducerIndexWithSelectedAnnotationFlags(@TempDir Path tempDir) throws Exception {
        Path testOutputDirectory = QuarkusJandexFixtureCompiler.compile(
                tempDir.resolve("test-classes"),
                Map.of(
                        "com.example.BlueProfile", """
                                package com.example;

                                public class BlueProfile implements io.quarkus.test.junit.QuarkusTestProfile {
                                }
                                """,
                        "com.example.ProfiledQuarkusCase", """
                                package com.example;

                                @io.quarkus.test.junit.QuarkusTest
                                @io.quarkus.test.junit.TestProfile(BlueProfile.class)
                                public class ProfiledQuarkusCase {
                                }
                                """));

        try (FixtureRuntime runtime = QuarkusJandexFixtureCompiler.fixtureRuntime()) {
            Object index = runtime.index(testOutputDirectory);

            assertEquals(
                    "com.example.ProfiledQuarkusCase[index=true,quarkusTest=true,testProfile=true,vetoed=false],"
                            + "com.example.MissingCase[index=false,quarkusTest=false,testProfile=false,vetoed=false]",
                    formatProducerIndex(
                            runtime,
                            index,
                            java.util.List.of("com.example.ProfiledQuarkusCase", "com.example.MissingCase")));
        }
    }

    @Test
    void formatsCombinedIndexWithComputingIndexFallback(@TempDir Path tempDir) throws Exception {
        Path baseOutputDirectory = QuarkusJandexFixtureCompiler.compile(
                tempDir.resolve("base-classes"),
                Map.of("com.example.PlainCase", """
                        package com.example;

                        public class PlainCase {
                        }
                        """));
        Path computingOutputDirectory = QuarkusJandexFixtureCompiler.compile(
                tempDir.resolve("computing-classes"),
                Map.of("com.example.ComputingQuarkusCase", """
                        package com.example;

                        @io.quarkus.test.junit.QuarkusTest
                        public class ComputingQuarkusCase {
                        }
                        """));

        try (FixtureRuntime runtime = QuarkusJandexFixtureCompiler.fixtureRuntime()) {
            Object baseIndex = runtime.index(baseOutputDirectory);
            Object computingIndex = runtime.index(computingOutputDirectory);

            assertEquals(
                    "com.example.ComputingQuarkusCase[index=false,computing=true,quarkusTest=true,testProfile=false,vetoed=false],"
                            + "com.example.PlainCase[index=true,computing=false,quarkusTest=false,testProfile=false,vetoed=false]",
                    formatCombinedIndex(
                            runtime,
                            new FakeCombinedIndex(baseIndex, computingIndex),
                            java.util.List.of("com.example.ComputingQuarkusCase", "com.example.PlainCase")));
        }
    }

    @Test
    void formatsMissingAndEmptyIndexes() throws Exception {
        try (FixtureRuntime runtime = QuarkusJandexFixtureCompiler.fixtureRuntime()) {
            assertEquals(
                    "<missing>",
                    formatCombinedIndex(runtime, null, java.util.List.of("com.example.Case")));
            assertEquals(
                    "<none>",
                    formatProducerIndex(runtime, runtime.emptyIndex(), java.util.List.of()));
        }
    }

    public static final class FakeCombinedIndex {
        private final Object index;
        private final Object computingIndex;

        FakeCombinedIndex(Object index, Object computingIndex) {
            this.index = index;
            this.computingIndex = computingIndex;
        }

        public Object getIndex() {
            return index;
        }

        public Object getComputingIndex() {
            return computingIndex;
        }
    }

    private static String formatProducerIndex(FixtureRuntime runtime, Object index, java.util.List<String> testClasses)
            throws Exception {
        Class<?> diagnosticClass =
                runtime.loadClass("sh.zolt.quarkus.annotation.diagnostic.QuarkusSelectedTestIndexDiagnostic");
        Method method = diagnosticClass.getMethod(
                "formatProducerIndex",
                runtime.loadClass("org.jboss.jandex.Index"),
                java.util.List.class);
        return (String) method.invoke(null, index, testClasses);
    }

    private static String formatCombinedIndex(FixtureRuntime runtime, Object combinedIndex, java.util.List<String> testClasses)
            throws Exception {
        Class<?> diagnosticClass =
                runtime.loadClass("sh.zolt.quarkus.annotation.diagnostic.QuarkusSelectedTestIndexDiagnostic");
        Method method = diagnosticClass.getMethod("formatCombinedIndex", Object.class, java.util.List.class);
        return (String) method.invoke(null, combinedIndex, testClasses);
    }
}
