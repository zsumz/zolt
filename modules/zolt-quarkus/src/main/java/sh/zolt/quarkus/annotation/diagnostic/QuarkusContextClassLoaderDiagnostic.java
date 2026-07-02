package sh.zolt.quarkus.annotation.diagnostic;

import sh.zolt.quarkus.annotation.ZoltQuarkusTestClassBeanCustomizer;
import java.util.List;

public final class QuarkusContextClassLoaderDiagnostic {
    private QuarkusContextClassLoaderDiagnostic() {
    }

    public static String currentClassLoader() {
        return ZoltQuarkusTestClassBeanCustomizer.classLoaderName(Thread.currentThread().getContextClassLoader());
    }

    public static String formatSelectedClasses(List<String> classNames) {
        if (classNames.isEmpty()) {
            return "<none>";
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classNames.stream()
                .map(className -> selectedClass(classLoader, className))
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String selectedClass(ClassLoader classLoader, String className) {
        try {
            Class<?> loadedClass = Class.forName(className, false, classLoader);
            return className
                    + "[loadable=true,loader="
                    + ZoltQuarkusTestClassBeanCustomizer.classLoaderName(loadedClass.getClassLoader())
                    + "]";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return className
                    + "[loadable=false,reason="
                    + exception.getClass().getSimpleName()
                    + "]";
        }
    }
}
