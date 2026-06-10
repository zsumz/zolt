package com.zolt.quarkus;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class QuarkusTestIndexProbe {
    public static final String MAIN_CLASS = "com.zolt.quarkus.QuarkusTestIndexProbe";

    private static final String QUARKUS_TEST_ANNOTATION = "io.quarkus.test.junit.QuarkusTest";
    private static final String EXTEND_WITH_ANNOTATION = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String JUNIT_TEST_ANNOTATION = "org.junit.jupiter.api.Test";

    public static void main(String[] args) {
        int exitCode = new QuarkusTestIndexProbe().run(args, System.out, System.err);
        System.exit(exitCode);
    }

    int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length < 2) {
            err.println("error: Quarkus test index probe requires a test output directory and at least one test class.");
            return 2;
        }
        Path testOutputDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(testOutputDirectory)) {
            err.println("error: Quarkus test index probe test output directory does not exist: "
                    + testOutputDirectory);
            return 2;
        }
        List<String> testClasses = Arrays.asList(Arrays.copyOfRange(args, 1, args.length));
        try {
            IndexProbeResult result = new ReflectiveIndexInspector().inspect(testOutputDirectory, testClasses);
            writeReport(result, out);
            return 0;
        } catch (ReflectiveOperationException | LinkageError exception) {
            err.println("error: Could not inspect Quarkus test class index. Check that quarkus-test-common, "
                    + "Jandex, Quarkus JUnit, and compiled tests are on the probe classpath.");
            exception.printStackTrace(err);
            return 1;
        }
    }

    static void writeReport(IndexProbeResult result, PrintStream out) {
        out.println("Quarkus test index probe");
        out.println("Test output: " + result.testOutputDirectory());
        out.println("Known classes: " + result.knownClassCount());
        out.println("Selected test classes:");
        for (SelectedClassIndexResult selectedClass : result.selectedClasses()) {
            out.println("  " + selectedClass.className()
                    + " present=" + selectedClass.present()
                    + " @QuarkusTest=" + selectedClass.hasQuarkusTest()
                    + " @ExtendWith=" + selectedClass.hasExtendWith());
            out.println("    annotations: " + annotationsLine(selectedClass.declaredAnnotations()));
        }
        out.println("Annotation counts:");
        result.annotationCounts().forEach((annotation, count) ->
                out.println("  " + annotation + "=" + count));
    }

    private static String annotationsLine(List<String> annotations) {
        if (annotations.isEmpty()) {
            return "<none>";
        }
        return String.join(", ", annotations);
    }

    record IndexProbeResult(
            Path testOutputDirectory,
            int knownClassCount,
            List<SelectedClassIndexResult> selectedClasses,
            Map<String, Integer> annotationCounts) {
    }

    record SelectedClassIndexResult(
            String className,
            boolean present,
            boolean hasQuarkusTest,
            boolean hasExtendWith,
            List<String> declaredAnnotations) {
    }

    private static final class ReflectiveIndexInspector {
        private final Class<?> dotNameClass;
        private final Method createSimpleDotName;

        private ReflectiveIndexInspector() throws ReflectiveOperationException {
            this.dotNameClass = Class.forName("org.jboss.jandex.DotName");
            this.createSimpleDotName = dotNameClass.getMethod("createSimple", String.class);
        }

        private IndexProbeResult inspect(Path testOutputDirectory, List<String> testClasses)
                throws ReflectiveOperationException {
            Object index = createIndex(testOutputDirectory);
            Collection<?> knownClasses = knownClasses(index);
            List<SelectedClassIndexResult> selectedClasses = new ArrayList<>();
            for (String testClass : testClasses) {
                selectedClasses.add(inspectClass(index, testClass));
            }
            return new IndexProbeResult(
                    testOutputDirectory,
                    knownClasses.size(),
                    List.copyOf(selectedClasses),
                    annotationCounts(index));
        }

        private Object createIndex(Path testOutputDirectory) throws ReflectiveOperationException {
            Class<?> testClassIndexer = Class.forName("io.quarkus.test.common.TestClassIndexer");
            return testClassIndexer
                    .getMethod("indexTestClasses", Path.class)
                    .invoke(null, testOutputDirectory);
        }

        private SelectedClassIndexResult inspectClass(Object index, String className)
                throws ReflectiveOperationException {
            Object classInfo = index.getClass()
                    .getMethod("getClassByName", dotNameClass)
                    .invoke(index, dotName(className));
            if (classInfo == null) {
                return new SelectedClassIndexResult(className, false, false, false, List.of());
            }
            return new SelectedClassIndexResult(
                    className,
                    true,
                    hasClassAnnotation(classInfo, QUARKUS_TEST_ANNOTATION),
                    hasClassAnnotation(classInfo, EXTEND_WITH_ANNOTATION),
                    classAnnotationNames(classInfo));
        }

        private Map<String, Integer> annotationCounts(Object index) throws ReflectiveOperationException {
            Map<String, Integer> counts = new TreeMap<>();
            for (String annotation : List.of(
                    QUARKUS_TEST_ANNOTATION,
                    EXTEND_WITH_ANNOTATION,
                    JUNIT_TEST_ANNOTATION)) {
                Collection<?> annotations = annotations(index, annotation);
                counts.put(annotation, annotations.size());
            }
            return counts;
        }

        private boolean hasClassAnnotation(Object classInfo, String annotationName)
                throws ReflectiveOperationException {
            Object annotation = classInfo.getClass()
                    .getMethod("classAnnotation", dotNameClass)
                    .invoke(classInfo, dotName(annotationName));
            return annotation != null;
        }

        private List<String> classAnnotationNames(Object classInfo) throws ReflectiveOperationException {
            Collection<?> annotations = (Collection<?>) classInfo.getClass()
                    .getMethod("classAnnotations")
                    .invoke(classInfo);
            List<String> names = new ArrayList<>();
            for (Object annotation : annotations) {
                Object name = annotation.getClass().getMethod("name").invoke(annotation);
                names.add(String.valueOf(name));
            }
            names.sort(Comparator.naturalOrder());
            return List.copyOf(names);
        }

        private Collection<?> annotations(Object index, String annotationName)
                throws ReflectiveOperationException {
            return (Collection<?>) index.getClass()
                    .getMethod("getAnnotations", dotNameClass)
                    .invoke(index, dotName(annotationName));
        }

        private Collection<?> knownClasses(Object index) throws ReflectiveOperationException {
            return (Collection<?>) index.getClass().getMethod("getKnownClasses").invoke(index);
        }

        private Object dotName(String name) throws ReflectiveOperationException {
            return createSimpleDotName.invoke(null, name);
        }
    }
}
