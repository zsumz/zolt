package com.zolt.quarkus.annotation;

import java.lang.reflect.Method;
import java.util.List;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;

final class QuarkusSelectedTestIndexDiagnostic {
    private static final DotName QUARKUS_TEST = DotName.createSimple("io.quarkus.test.junit.QuarkusTest");
    private static final DotName TEST_PROFILE = DotName.createSimple("io.quarkus.test.junit.TestProfile");
    private static final DotName VETOED = DotName.createSimple("jakarta.enterprise.inject.Vetoed");

    private QuarkusSelectedTestIndexDiagnostic() {
    }

    static String formatCombinedIndex(
            Object combinedIndex,
            List<String> testClasses) {
        if (combinedIndex == null) {
            return "<missing>";
        }
        if (testClasses.isEmpty()) {
            return "<none>";
        }
        IndexView index = indexView(combinedIndex, "getIndex");
        IndexView computingIndex = indexView(combinedIndex, "getComputingIndex");
        return testClasses.stream()
                .map(testClass -> selectedClassIndexDiagnostic(index, computingIndex, testClass))
                .collect(java.util.stream.Collectors.joining(","));
    }

    static String formatProducerIndex(
            Index index,
            List<String> testClasses) {
        if (index == null) {
            return "<missing>";
        }
        if (testClasses.isEmpty()) {
            return "<none>";
        }
        return testClasses.stream()
                .map(testClass -> selectedClassIndexDiagnostic(index, testClass))
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String selectedClassIndexDiagnostic(
            IndexView index,
            IndexView computingIndex,
            String testClass) {
        ClassInfo indexClass = classByName(index, testClass);
        ClassInfo computingClass = classByName(computingIndex, testClass);
        ClassInfo annotationClass = computingClass == null ? indexClass : computingClass;
        return testClass
                + "[index="
                + (indexClass != null)
                + ",computing="
                + (computingClass != null)
                + ",quarkusTest="
                + hasAnnotation(annotationClass, QUARKUS_TEST)
                + ",testProfile="
                + hasAnnotation(annotationClass, TEST_PROFILE)
                + ",vetoed="
                + hasAnnotation(annotationClass, VETOED)
                + "]";
    }

    private static IndexView indexView(Object combinedIndex, String methodName) {
        try {
            Method method = combinedIndex.getClass().getMethod(methodName);
            Object value = method.invoke(combinedIndex);
            return value instanceof IndexView indexView ? indexView : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }

    private static String selectedClassIndexDiagnostic(
            Index index,
            String testClass) {
        ClassInfo indexClass = classByName(index, testClass);
        return testClass
                + "[index="
                + (indexClass != null)
                + ",quarkusTest="
                + hasAnnotation(indexClass, QUARKUS_TEST)
                + ",testProfile="
                + hasAnnotation(indexClass, TEST_PROFILE)
                + ",vetoed="
                + hasAnnotation(indexClass, VETOED)
                + "]";
    }

    private static ClassInfo classByName(IndexView index, String className) {
        return index == null ? null : index.getClassByName(DotName.createSimple(className));
    }

    private static boolean hasAnnotation(ClassInfo classInfo, DotName annotationName) {
        return classInfo != null && classInfo.hasAnnotation(annotationName);
    }
}
