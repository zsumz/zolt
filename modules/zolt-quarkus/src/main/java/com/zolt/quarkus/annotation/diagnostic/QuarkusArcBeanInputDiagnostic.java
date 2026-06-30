package com.zolt.quarkus.annotation.diagnostic;

import com.zolt.quarkus.annotation.ZoltQuarkusTestClassBeanCustomizer;
import io.quarkus.builder.BuildContext;
import java.lang.reflect.Method;
import java.util.List;

public final class QuarkusArcBeanInputDiagnostic {
    private QuarkusArcBeanInputDiagnostic() {
    }

    public static List<?> consumeMulti(BuildContext context, Class<?> buildItemClass) {
        try {
            Method consumeMulti = context.getClass().getMethod("consumeMulti", Class.class);
            Object items = consumeMulti.invoke(context, buildItemClass);
            return items instanceof List<?> list ? list : List.of();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            ZoltQuarkusTestClassBeanCustomizer.writeDiagnostic(
                    buildItemClass.getSimpleName() + ".consumeMulti=false",
                    buildItemClass.getSimpleName() + ".consumeMultiReason=" + exception.getClass().getSimpleName());
            return List.of();
        }
    }

    public static String formatTestClassBeanItems(List<?> items, List<String> testClasses) {
        if (testClasses.isEmpty()) {
            return "count=" + items.size() + ",selected=<none>";
        }
        return "count="
                + items.size()
                + ",selected="
                + testClasses.stream()
                        .map(testClass -> testClass + "[items=" + testClassItemCount(items, testClass) + "]")
                        .collect(java.util.stream.Collectors.joining(","));
    }

    public static String formatAdditionalBeanItems(List<?> items, List<String> testClasses) {
        if (testClasses.isEmpty()) {
            return "count=" + items.size() + ",selected=<none>";
        }
        return "count="
                + items.size()
                + ",selected="
                + testClasses.stream()
                        .map(testClass -> additionalBeanItemDiagnostic(items, testClass))
                        .collect(java.util.stream.Collectors.joining(","));
    }

    private static long testClassItemCount(List<?> items, String testClass) {
        return items.stream()
                .filter(item -> testClass.equals(stringValue(item, "getTestClassName")))
                .count();
    }

    private static String additionalBeanItemDiagnostic(List<?> items, String testClass) {
        long presentItems = items.stream()
                .filter(item -> beanClasses(item).contains(testClass))
                .count();
        long unremovableItems = items.stream()
                .filter(item -> beanClasses(item).contains(testClass))
                .filter(item -> !booleanValue(item, "isRemovable", true))
                .count();
        long removableItems = presentItems - unremovableItems;
        String defaultScopes = items.stream()
                .filter(item -> beanClasses(item).contains(testClass))
                .map(item -> objectValue(item, "getDefaultScope"))
                .map(scope -> scope == null ? "<none>" : scope.toString())
                .distinct()
                .collect(java.util.stream.Collectors.joining("|"));
        if (defaultScopes.isBlank()) {
            defaultScopes = "<none>";
        }
        return testClass
                + "[items="
                + presentItems
                + ",unremovable="
                + unremovableItems
                + ",removable="
                + removableItems
                + ",defaultScopes="
                + defaultScopes
                + "]";
    }

    private static List<?> beanClasses(Object item) {
        Object value = objectValue(item, "getBeanClasses");
        return value instanceof List<?> list ? list : List.of();
    }

    private static String stringValue(Object item, String methodName) {
        Object value = objectValue(item, methodName);
        return value == null ? "" : value.toString();
    }

    private static boolean booleanValue(Object item, String methodName, boolean fallback) {
        Object value = objectValue(item, methodName);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static Object objectValue(Object item, String methodName) {
        try {
            return item.getClass().getMethod(methodName).invoke(item);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }
}
