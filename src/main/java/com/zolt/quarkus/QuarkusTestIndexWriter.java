package com.zolt.quarkus;

import java.lang.reflect.Method;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class QuarkusTestIndexWriter {
    private static final String QUARKUS_TEST_ANNOTATION = "io.quarkus.test.junit.QuarkusTest";
    private static final String EXTEND_WITH_ANNOTATION = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String QUARKUS_TEST_EXTENSION = "io.quarkus.test.junit.QuarkusTestExtension";
    private static final String ANNOTATION_VALUE = "value";

    Object createIndex(Path testOutputDirectory, List<String> testClasses)
            throws ReflectiveOperationException {
        ReflectiveJandex jandex = new ReflectiveJandex();
        Object index = createRawIndex(testOutputDirectory);
        if (testClasses.isEmpty()) {
            return index;
        }
        return jandex.enrichQuarkusTestExtensionCandidates(index, testClasses);
    }

    Object createRawIndex(Path testOutputDirectory) throws ReflectiveOperationException {
        return new ReflectiveJandex().indexTestClasses(testOutputDirectory);
    }

    void write(Path testOutputDirectory, List<String> testClasses)
            throws ReflectiveOperationException {
        if (testClasses.isEmpty()) {
            return;
        }
        ReflectiveJandex jandex = new ReflectiveJandex();
        Object index = jandex.enrichQuarkusTestExtensionCandidates(
                jandex.indexTestClasses(testOutputDirectory),
                testClasses);
        Class<?> firstTestClass = Class.forName(
                testClasses.getFirst(),
                false,
                ClassLoader.getSystemClassLoader());
        jandex.writeIndex(index, testOutputDirectory, firstTestClass);
    }

    private static final class ReflectiveJandex {
        private final Class<?> dotNameClass;
        private final Class<?> classInfoClass;
        private final Class<?> annotationInstanceClass;
        private final Class<?> annotationValueClass;
        private final Class<?> typeClass;
        private final Class<?> typeKindClass;
        private final Class<?> indexClass;
        private final Class<?> testClassIndexerClass;
        private final Method createSimpleDotName;

        private ReflectiveJandex() throws ReflectiveOperationException {
            this.dotNameClass = Class.forName("org.jboss.jandex.DotName");
            this.classInfoClass = Class.forName("org.jboss.jandex.ClassInfo");
            this.annotationInstanceClass = Class.forName("org.jboss.jandex.AnnotationInstance");
            this.annotationValueClass = Class.forName("org.jboss.jandex.AnnotationValue");
            this.typeClass = Class.forName("org.jboss.jandex.Type");
            this.typeKindClass = Class.forName("org.jboss.jandex.Type$Kind");
            this.indexClass = Class.forName("org.jboss.jandex.Index");
            this.testClassIndexerClass = Class.forName("io.quarkus.test.common.TestClassIndexer");
            this.createSimpleDotName = dotNameClass.getMethod("createSimple", String.class);
        }

        private Object indexTestClasses(Path testOutputDirectory) throws ReflectiveOperationException {
            return testClassIndexerClass
                    .getMethod("indexTestClasses", Path.class)
                    .invoke(null, testOutputDirectory);
        }

        private void writeIndex(Object index, Path testOutputDirectory, Class<?> firstTestClass)
                throws ReflectiveOperationException {
            testClassIndexerClass
                    .getMethod("writeIndex", indexClass, Path.class, Class.class)
                    .invoke(null, index, testOutputDirectory, firstTestClass);
        }

        private Object enrichQuarkusTestExtensionCandidates(Object index, List<String> testClasses)
                throws ReflectiveOperationException {
            List<Object> syntheticExtendWithAnnotations = syntheticExtendWithAnnotations(index, testClasses);
            if (syntheticExtendWithAnnotations.isEmpty()) {
                return index;
            }
            Map<Object, List<Object>> annotations = annotationMap(index);
            Object extendWith = dotName(EXTEND_WITH_ANNOTATION);
            annotations.computeIfAbsent(extendWith, ignored -> new ArrayList<>())
                    .addAll(syntheticExtendWithAnnotations);
            return createIndex(
                    annotations,
                    subclasses(index),
                    subinterfaces(index),
                    implementors(index),
                    classes(index),
                    users(index));
        }

        private List<Object> syntheticExtendWithAnnotations(Object index, List<String> testClasses)
                throws ReflectiveOperationException {
            List<Object> annotations = new ArrayList<>();
            Object quarkusTest = dotName(QUARKUS_TEST_ANNOTATION);
            Object extendWith = dotName(EXTEND_WITH_ANNOTATION);
            for (String testClass : testClasses) {
                Object classInfo = classByName(index, testClass);
                if (classInfo == null
                        || !hasClassAnnotation(classInfo, quarkusTest)
                        || hasExtendWithCandidate(index, testClass)
                        || hasClassAnnotation(classInfo, extendWith)) {
                    continue;
                }
                annotations.add(createExtendWithAnnotation(classInfo));
            }
            return annotations;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Object createExtendWithAnnotation(Object classInfo) throws ReflectiveOperationException {
            Object classKind = Enum.valueOf(typeKindClass.asSubclass(Enum.class), "CLASS");
            Object extensionType = typeClass
                    .getMethod("create", dotNameClass, typeKindClass)
                    .invoke(null, dotName(QUARKUS_TEST_EXTENSION), classKind);
            Object extensionValue = annotationValueClass
                    .getMethod("createClassValue", String.class, typeClass)
                    .invoke(null, ANNOTATION_VALUE, extensionType);
            Object extensionValues = Array.newInstance(annotationValueClass, 1);
            Array.set(extensionValues, 0, extensionValue);
            Object arrayValue = annotationValueClass
                    .getMethod("createArrayValue", String.class, annotationValueClass.arrayType())
                    .invoke(null, ANNOTATION_VALUE, extensionValues);
            Object annotationValues = Array.newInstance(annotationValueClass, 1);
            Array.set(annotationValues, 0, arrayValue);
            return annotationInstanceClass
                    .getMethod("create", dotNameClass, Class.forName("org.jboss.jandex.AnnotationTarget"), annotationValueClass.arrayType())
                    .invoke(null, dotName(EXTEND_WITH_ANNOTATION), classInfo, annotationValues);
        }

        private Object createIndex(
                Map<Object, List<Object>> annotations,
                Map<Object, List<Object>> subclasses,
                Map<Object, List<Object>> subinterfaces,
                Map<Object, List<Object>> implementors,
                Map<Object, Object> classes,
                Map<Object, List<Object>> users) throws ReflectiveOperationException {
            return indexClass
                    .getMethod("create", Map.class, Map.class, Map.class, Map.class, Map.class, Map.class)
                    .invoke(null, annotations, subclasses, subinterfaces, implementors, classes, users);
        }

        private Map<Object, List<Object>> annotationMap(Object index) throws ReflectiveOperationException {
            Map<Object, List<Object>> annotations = new LinkedHashMap<>();
            for (Object classInfo : knownClasses(index)) {
                Map<?, ?> classAnnotations = (Map<?, ?>) classInfo.getClass()
                        .getMethod("annotationsMap")
                        .invoke(classInfo);
                for (Map.Entry<?, ?> entry : classAnnotations.entrySet()) {
                    annotations.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                            .addAll((Collection<?>) entry.getValue());
                }
            }
            return annotations;
        }

        private Map<Object, Object> classes(Object index) throws ReflectiveOperationException {
            Map<Object, Object> classes = new LinkedHashMap<>();
            for (Object classInfo : knownClasses(index)) {
                classes.put(classInfo.getClass().getMethod("name").invoke(classInfo), classInfo);
            }
            return classes;
        }

        private Map<Object, List<Object>> subclasses(Object index) throws ReflectiveOperationException {
            Map<Object, List<Object>> subclasses = new LinkedHashMap<>();
            Object objectName = dotName("java.lang.Object");
            for (Object classInfo : knownClasses(index)) {
                Object superName = classInfo.getClass().getMethod("superName").invoke(classInfo);
                if (superName != null && !superName.equals(objectName)) {
                    subclasses.computeIfAbsent(superName, ignored -> new ArrayList<>()).add(classInfo);
                }
            }
            return subclasses;
        }

        private Map<Object, List<Object>> subinterfaces(Object index) throws ReflectiveOperationException {
            Map<Object, List<Object>> subinterfaces = new LinkedHashMap<>();
            for (Object classInfo : knownClasses(index)) {
                boolean isInterface = (boolean) classInfo.getClass().getMethod("isInterface").invoke(classInfo);
                if (!isInterface) {
                    continue;
                }
                for (Object interfaceName : interfaceNames(classInfo)) {
                    subinterfaces.computeIfAbsent(interfaceName, ignored -> new ArrayList<>()).add(classInfo);
                }
            }
            return subinterfaces;
        }

        private Map<Object, List<Object>> implementors(Object index) throws ReflectiveOperationException {
            Map<Object, List<Object>> implementors = new LinkedHashMap<>();
            for (Object classInfo : knownClasses(index)) {
                boolean isInterface = (boolean) classInfo.getClass().getMethod("isInterface").invoke(classInfo);
                if (isInterface) {
                    continue;
                }
                for (Object interfaceName : interfaceNames(classInfo)) {
                    implementors.computeIfAbsent(interfaceName, ignored -> new ArrayList<>()).add(classInfo);
                }
            }
            return implementors;
        }

        private Map<Object, List<Object>> users(Object index) {
            return new LinkedHashMap<>();
        }

        private List<?> interfaceNames(Object classInfo) throws ReflectiveOperationException {
            return (List<?>) classInfo.getClass().getMethod("interfaceNames").invoke(classInfo);
        }

        private boolean hasClassAnnotation(Object classInfo, Object annotationName)
                throws ReflectiveOperationException {
            Object annotation = classInfo.getClass()
                    .getMethod("classAnnotation", dotNameClass)
                    .invoke(classInfo, annotationName);
            return annotation != null;
        }

        private boolean hasExtendWithCandidate(Object index, String className)
                throws ReflectiveOperationException {
            Object extendWith = dotName(EXTEND_WITH_ANNOTATION);
            Object selectedName = dotName(className);
            for (Object annotation : annotations(index, extendWith)) {
                Object target = annotation.getClass().getMethod("target").invoke(annotation);
                Object kind = target.getClass().getMethod("kind").invoke(target);
                if (!"CLASS".equals(String.valueOf(kind))) {
                    continue;
                }
                Object targetClass = target.getClass().getMethod("asClass").invoke(target);
                boolean annotationType = (boolean) targetClass.getClass().getMethod("isAnnotation").invoke(targetClass);
                Object targetName = targetClass.getClass().getMethod("name").invoke(targetClass);
                if (!annotationType && selectedName.equals(targetName)) {
                    return true;
                }
            }
            return false;
        }

        private Object classByName(Object index, String className) throws ReflectiveOperationException {
            return index.getClass()
                    .getMethod("getClassByName", dotNameClass)
                    .invoke(index, dotName(className));
        }

        private Collection<?> annotations(Object index, Object annotationName) throws ReflectiveOperationException {
            return (Collection<?>) index.getClass()
                    .getMethod("getAnnotations", dotNameClass)
                    .invoke(index, annotationName);
        }

        private Collection<?> knownClasses(Object index) throws ReflectiveOperationException {
            return (Collection<?>) index.getClass().getMethod("getKnownClasses").invoke(index);
        }

        private Object dotName(String name) throws ReflectiveOperationException {
            return createSimpleDotName.invoke(null, name);
        }
    }
}
