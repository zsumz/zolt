package sh.zolt.quarkus.annotation;

import sh.zolt.error.WorkerFailureDiagnostic;
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
    public static final String MAIN_CLASS = "sh.zolt.quarkus.annotation.QuarkusTestIndexProbe";

    private static final String QUARKUS_TEST_ANNOTATION = "io.quarkus.test.junit.QuarkusTest";
    private static final String QUARKUS_TEST_EXTENSION = "io.quarkus.test.junit.QuarkusTestExtension";
    private static final String EXTEND_WITH_ANNOTATION = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String REGISTER_EXTENSION_ANNOTATION = "org.junit.jupiter.api.extension.RegisterExtension";
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
            err.println(WorkerFailureDiagnostic.causeLine(exception));
            return 1;
        }
    }

    static void writeReport(IndexProbeResult result, PrintStream out) {
        out.println("Quarkus test index probe");
        out.println("Test output: " + result.testOutputDirectory());
        out.println("Class locations:");
        for (TestClassLocationResult location : result.classLocations()) {
            out.println("  " + location.className() + " location=" + location.location());
        }
        writeSnapshot(result.rawIndex(), out);
        writeSnapshot(result.enrichedIndex(), out);
    }

    private static void writeSnapshot(IndexProbeSnapshot snapshot, PrintStream out) {
        out.println(snapshot.label() + " index:");
        out.println("  Known classes: " + snapshot.knownClassCount());
        out.println("  Selected test classes:");
        for (SelectedClassIndexResult selectedClass : snapshot.selectedClasses()) {
            out.println("  " + selectedClass.className()
                    + " present=" + selectedClass.present()
                    + " @QuarkusTest=" + selectedClass.hasQuarkusTest()
                    + " @ExtendWith=" + selectedClass.hasExtendWith());
            out.println("    annotations: " + annotationsLine(selectedClass.declaredAnnotations()));
        }
        out.println("  Annotation counts:");
        snapshot.annotationCounts().forEach((annotation, count) ->
                out.println("    " + annotation + "=" + count));
        out.println("  Build-chain test bean candidates:");
        if (snapshot.testClassBeanCandidates().isEmpty()) {
            out.println("    <none>");
        } else {
            snapshot.testClassBeanCandidates().forEach(candidate ->
                    out.println("    " + candidate));
        }
    }

    private static String annotationsLine(List<String> annotations) {
        if (annotations.isEmpty()) {
            return "<none>";
        }
        return String.join(", ", annotations);
    }

    record IndexProbeResult(
            Path testOutputDirectory,
            List<TestClassLocationResult> classLocations,
            IndexProbeSnapshot rawIndex,
            IndexProbeSnapshot enrichedIndex) {
    }

    record IndexProbeSnapshot(
            String label,
            int knownClassCount,
            List<SelectedClassIndexResult> selectedClasses,
            Map<String, Integer> annotationCounts,
            List<String> testClassBeanCandidates) {
    }

    record TestClassLocationResult(
            String className,
            String location) {
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
        private final Method annotationValueAsClassArray;

        private ReflectiveIndexInspector() throws ReflectiveOperationException {
            this.dotNameClass = Class.forName("org.jboss.jandex.DotName");
            Class<?> annotationValueClass = Class.forName("org.jboss.jandex.AnnotationValue");
            this.createSimpleDotName = dotNameClass.getMethod("createSimple", String.class);
            this.annotationValueAsClassArray = annotationValueClass.getMethod("asClassArray");
        }

        private IndexProbeResult inspect(Path testOutputDirectory, List<String> testClasses)
                throws ReflectiveOperationException {
            Object rawIndex = createRawIndex(testOutputDirectory);
            Object enrichedIndex = createIndex(testOutputDirectory, testClasses);
            return new IndexProbeResult(
                    testOutputDirectory,
                    classLocations(testClasses),
                    inspectSnapshot("Raw", rawIndex, testClasses),
                    inspectSnapshot("Enriched", enrichedIndex, testClasses));
        }

        private IndexProbeSnapshot inspectSnapshot(String label, Object index, List<String> testClasses)
                throws ReflectiveOperationException {
            Collection<?> knownClasses = knownClasses(index);
            List<SelectedClassIndexResult> selectedClasses = new ArrayList<>();
            for (String testClass : testClasses) {
                selectedClasses.add(inspectClass(index, testClass));
            }
            return new IndexProbeSnapshot(
                    label,
                    knownClasses.size(),
                    List.copyOf(selectedClasses),
                    annotationCounts(index),
                    testClassBeanCandidates(index));
        }

        private List<TestClassLocationResult> classLocations(List<String> testClasses) {
            List<TestClassLocationResult> locations = new ArrayList<>();
            for (String testClass : testClasses) {
                locations.add(classLocation(testClass));
            }
            return List.copyOf(locations);
        }

        private TestClassLocationResult classLocation(String testClass) {
            try {
                Class<?> selectedClass = Class.forName(testClass, false, ClassLoader.getSystemClassLoader());
                Class<?> pathTestHelper = Class.forName("io.quarkus.test.common.PathTestHelper");
                Object location = pathTestHelper
                        .getMethod("getTestClassesLocation", Class.class)
                        .invoke(null, selectedClass);
                return new TestClassLocationResult(testClass, String.valueOf(location));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                return new TestClassLocationResult(
                        testClass,
                        "<unavailable: " + exception.getClass().getSimpleName() + ">");
            }
        }

        private Object createRawIndex(Path testOutputDirectory) throws ReflectiveOperationException {
            return new QuarkusTestIndexWriter().createRawIndex(testOutputDirectory);
        }

        private Object createIndex(Path testOutputDirectory, List<String> testClasses)
                throws ReflectiveOperationException {
            return new QuarkusTestIndexWriter().createIndex(testOutputDirectory, testClasses);
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
                    hasExtendWithCandidate(index, className),
                    classAnnotationNames(classInfo));
        }

        private Map<String, Integer> annotationCounts(Object index) throws ReflectiveOperationException {
            Map<String, Integer> counts = new TreeMap<>();
            for (String annotation : List.of(
                    QUARKUS_TEST_ANNOTATION,
                    EXTEND_WITH_ANNOTATION,
                    REGISTER_EXTENSION_ANNOTATION,
                    JUNIT_TEST_ANNOTATION)) {
                Collection<?> annotations = annotations(index, annotation);
                counts.put(annotation, annotations.size());
            }
            return counts;
        }

        private List<String> testClassBeanCandidates(Object index) throws ReflectiveOperationException {
            List<String> candidates = new ArrayList<>();
            addExtendWithCandidates(index, candidates);
            addRegisterExtensionCandidates(index, candidates);
            candidates.sort(Comparator.naturalOrder());
            return List.copyOf(candidates);
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

        private boolean hasExtendWithCandidate(Object index, String className)
                throws ReflectiveOperationException {
            Object selectedName = dotName(className);
            for (Object annotation : annotations(index, EXTEND_WITH_ANNOTATION)) {
                Object target = annotation.getClass().getMethod("target").invoke(annotation);
                Object kind = target.getClass().getMethod("kind").invoke(target);
                if (!"CLASS".equals(String.valueOf(kind))) {
                    continue;
                }
                Object targetClass = target.getClass().getMethod("asClass").invoke(target);
                boolean annotationType = (boolean) targetClass.getClass().getMethod("isAnnotation").invoke(targetClass);
                Object targetName = targetClass.getClass().getMethod("name").invoke(targetClass);
                if (!annotationType && selectedName.equals(targetName)
                        && extendWithUsesQuarkusTestExtension(annotation)) {
                    return true;
                }
            }
            return false;
        }

        private void addExtendWithCandidates(Object index, List<String> candidates)
                throws ReflectiveOperationException {
            for (Object annotation : annotations(index, EXTEND_WITH_ANNOTATION)) {
                Object target = annotation.getClass().getMethod("target").invoke(annotation);
                Object kind = target.getClass().getMethod("kind").invoke(target);
                if (!"CLASS".equals(String.valueOf(kind)) || !extendWithUsesQuarkusTestExtension(annotation)) {
                    continue;
                }
                Object targetClass = target.getClass().getMethod("asClass").invoke(target);
                boolean annotationType = (boolean) targetClass.getClass().getMethod("isAnnotation").invoke(targetClass);
                if (!annotationType) {
                    Object targetName = targetClass.getClass().getMethod("name").invoke(targetClass);
                    candidates.add(String.valueOf(targetName));
                }
            }
        }

        private void addRegisterExtensionCandidates(Object index, List<String> candidates)
                throws ReflectiveOperationException {
            for (Object annotation : annotations(index, REGISTER_EXTENSION_ANNOTATION)) {
                Object target = annotation.getClass().getMethod("target").invoke(annotation);
                Object kind = target.getClass().getMethod("kind").invoke(target);
                if (!"FIELD".equals(String.valueOf(kind))) {
                    continue;
                }
                Object field = target.getClass().getMethod("asField").invoke(target);
                Object fieldType = field.getClass().getMethod("type").invoke(field);
                Object fieldTypeName = fieldType.getClass().getMethod("name").invoke(fieldType);
                if (!QUARKUS_TEST_EXTENSION.equals(String.valueOf(fieldTypeName))) {
                    continue;
                }
                Object declaringClass = field.getClass().getMethod("declaringClass").invoke(field);
                Object declaringClassName = declaringClass.getClass().getMethod("name").invoke(declaringClass);
                candidates.add(String.valueOf(declaringClassName));
            }
        }

        private boolean extendWithUsesQuarkusTestExtension(Object annotation)
                throws ReflectiveOperationException {
            Object value = annotation.getClass().getMethod("value").invoke(annotation);
            if (value == null) {
                return false;
            }
            Object[] classes = (Object[]) annotationValueAsClassArray.invoke(value);
            for (Object type : classes) {
                Object typeName = type.getClass().getMethod("name").invoke(type);
                if (QUARKUS_TEST_EXTENSION.equals(String.valueOf(typeName))) {
                    return true;
                }
            }
            return false;
        }

        private Collection<?> knownClasses(Object index) throws ReflectiveOperationException {
            return (Collection<?>) index.getClass().getMethod("getKnownClasses").invoke(index);
        }

        private Object dotName(String name) throws ReflectiveOperationException {
            return createSimpleDotName.invoke(null, name);
        }
    }
}
