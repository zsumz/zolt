package com.zolt.quarkus.annotation;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

final class QuarkusResourceElementDiagnostic {
    private final PrintStream out;

    QuarkusResourceElementDiagnostic(PrintStream out) {
        this.out = out;
    }

    void write(String label, String className, ClassLoader classLoader) {
        if (classLoader == null) {
            out.println("  " + label + ".resourceElements=<unavailable: missing classloader>");
            return;
        }
        String resourceName = className.replace('.', '/') + ".class";
        out.println("  " + label + ".resource=" + resourceName);
        try {
            Method elements = classLoader.getClass().getMethod("getElementsWithResource", String.class, boolean.class);
            elements.setAccessible(true);
            Object result = elements.invoke(classLoader, resourceName, true);
            if (!(result instanceof List<?> list)) {
                out.println("  " + label + ".resourceElements=<unavailable: unexpected result>");
                return;
            }
            if (list.isEmpty()) {
                out.println("  " + label + ".resourceElements=<none>");
                return;
            }
            for (int index = 0; index < list.size(); index++) {
                out.println("  " + label + ".resourceElement." + index + "=" + classPathElementName(list.get(index)));
            }
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            String causeName = cause == null
                    ? exception.getClass().getSimpleName()
                    : cause.getClass().getSimpleName();
            out.println("  " + label + ".resourceElements=<unavailable: " + causeName + ">");
        } catch (ReflectiveOperationException | LinkageError exception) {
            out.println("  " + label + ".resourceElements=<unavailable: "
                    + exception.getClass().getSimpleName()
                    + ">");
        }
    }

    private String classPathElementName(Object element) {
        try {
            Method root = element.getClass().getMethod("getRoot");
            root.setAccessible(true);
            Object value = root.invoke(element);
            if (value != null) {
                return String.valueOf(value);
            }
            return element.getClass().getName() + "(root=null)";
        } catch (ReflectiveOperationException | LinkageError exception) {
            return element.getClass().getName() + "@"
                    + Integer.toHexString(System.identityHashCode(element));
        }
    }
}
