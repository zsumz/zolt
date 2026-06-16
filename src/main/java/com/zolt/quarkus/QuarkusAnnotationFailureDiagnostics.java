package com.zolt.quarkus;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

final class QuarkusAnnotationFailureDiagnostics {
    private static final String MAIN_OUTPUT_DIRECTORY_PROPERTY =
            QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY;
    private static final String TEST_BUILD_CHAIN_FUNCTION = "io.quarkus.test.junit.TestBuildChainFunction";
    private static final String TEST_BUILD_CHAIN_CUSTOMIZER_SPI =
            "io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer";
    private static final String TEST_BUILD_CHAIN_CUSTOMIZER_SERVICE =
            "META-INF/services/io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer";

    private final PrintStream out;
    private final QuarkusDiagnosticFilePrinter diagnosticFilePrinter;

    QuarkusAnnotationFailureDiagnostics(PrintStream out) {
        this.out = out;
        this.diagnosticFilePrinter = new QuarkusDiagnosticFilePrinter(out);
    }

    void writeFailure(List<String> testClasses, ClassLoader quarkusRuntimeClassLoader, Throwable failure) {
        writeClassLoaderDiagnostic(testClasses, quarkusRuntimeClassLoader, failure);
        writeBuildChainCustomizerDiagnostic(quarkusRuntimeClassLoader);
        diagnosticFilePrinter.writeTestClassBeanCustomizerDiagnostic();
        diagnosticFilePrinter.writeBuildGraphDiagnostic();
    }

    Optional<String> failingClass(Throwable failure) {
        if (failure == null) {
            return Optional.empty();
        }
        Optional<String> ownClass = failingClassName(failure);
        if (ownClass.isPresent()) {
            return ownClass;
        }
        Optional<String> causeClass = failingClass(failure.getCause());
        if (causeClass.isPresent()) {
            return causeClass;
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            Optional<String> suppressedClass = failingClass(suppressed);
            if (suppressedClass.isPresent()) {
                return suppressedClass;
            }
        }
        return Optional.empty();
    }

    Optional<String> generatedInvokerClass(Throwable failure) {
        if (failure == null) {
            return Optional.empty();
        }
        for (StackTraceElement element : failure.getStackTrace()) {
            String className = element.getClassName();
            if (className.contains("$quarkusrestinvoker$")) {
                return Optional.of(className);
            }
        }
        Optional<String> causeClass = generatedInvokerClass(failure.getCause());
        if (causeClass.isPresent()) {
            return causeClass;
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            Optional<String> suppressedClass = generatedInvokerClass(suppressed);
            if (suppressedClass.isPresent()) {
                return suppressedClass;
            }
        }
        return Optional.empty();
    }

    void writeClassLoaderDiagnostic(List<String> testClasses, ClassLoader quarkusRuntimeClassLoader, Throwable failure) {
        if (testClasses.isEmpty() || quarkusRuntimeClassLoader == null) {
            return;
        }
        String testClassName = testClasses.getFirst();
        out.println("Zolt Quarkus classloader diagnostic:");
        out.println("  selectedClass=" + testClassName);
        writeClassVisibility("selected", testClassName, quarkusRuntimeClassLoader);
        firstApplicationClass().ifPresent(className -> {
            out.println("  applicationClass=" + className);
            writeClassVisibility("application", className, quarkusRuntimeClassLoader);
        });
        failingClass(failure).ifPresent(className -> {
            out.println("  failingClass=" + className);
            writeClassVisibility("failing", className, quarkusRuntimeClassLoader);
        });
        generatedInvokerClass(failure).ifPresent(className -> {
            out.println("  generatedInvokerClass=" + className);
            writeClassVisibility("generatedInvoker", className, quarkusRuntimeClassLoader);
            writeClassLoaderShape("quarkusRuntime", quarkusRuntimeClassLoader);
            writeQuarkusResourceElements("generatedInvoker.quarkusRuntimeLoader", className, quarkusRuntimeClassLoader);
            firstApplicationClass()
                    .ifPresent(applicationClass -> writeQuarkusResourceElements(
                            "application.quarkusRuntimeLoader",
                            applicationClass,
                            quarkusRuntimeClassLoader));
            failingClass(failure)
                    .ifPresent(failingClass -> writeQuarkusResourceElements(
                            "failing.quarkusRuntimeLoader",
                            failingClass,
                            quarkusRuntimeClassLoader));
            loadedClassLoader(className, quarkusRuntimeClassLoader).ifPresent(classLoader -> {
                out.println("  generatedInvoker.loadedClassActualClassLoader=" + classLoaderName(classLoader));
                writeClassLoaderShape("generatedInvoker.actualLoader", classLoader);
                writeQuarkusResourceElements("generatedInvoker.actualLoader", className, classLoader);
                firstApplicationClass()
                        .ifPresent(applicationClass -> writeAlternateClassVisibility(
                                "application.generatedInvokerLoader",
                                applicationClass,
                                classLoader));
                firstApplicationClass()
                        .ifPresent(applicationClass -> writeQuarkusResourceElements(
                                "application.generatedInvokerLoader",
                                applicationClass,
                                classLoader));
                failingClass(failure)
                        .ifPresent(failingClass -> writeAlternateClassVisibility(
                                "failing.generatedInvokerLoader",
                                failingClass,
                                classLoader));
                failingClass(failure)
                        .ifPresent(failingClass -> writeQuarkusResourceElements(
                                "failing.generatedInvokerLoader",
                                failingClass,
                                classLoader));
                String moduleConfiguration = applyModuleConfigurationToClassloader(
                        quarkusRuntimeClassLoader,
                        classLoader);
                out.println("  generatedInvoker.moduleConfiguration=" + moduleConfiguration);
                if ("applied".equals(moduleConfiguration)) {
                    firstApplicationClass()
                            .ifPresent(applicationClass -> writeAlternateClassVisibility(
                                    "application.generatedInvokerLoaderAfterModuleConfig",
                                    applicationClass,
                                    classLoader));
                    failingClass(failure)
                            .ifPresent(failingClass -> writeAlternateClassVisibility(
                                    "failing.generatedInvokerLoaderAfterModuleConfig",
                                    failingClass,
                                    classLoader));
                }
            });
        });
        runningApplicationClassLoader().ifPresent(classLoader -> {
            out.println("  runningApplicationClassLoader=" + classLoaderName(classLoader));
            if (classLoader != quarkusRuntimeClassLoader) {
                firstApplicationClass()
                        .ifPresent(className -> writeAlternateClassVisibility(
                                "application.runningApplication",
                                className,
                                classLoader));
                failingClass(failure)
                        .ifPresent(className -> writeAlternateClassVisibility(
                                "failing.runningApplication",
                                className,
                                classLoader));
                generatedInvokerClass(failure)
                        .ifPresent(className -> writeAlternateClassVisibility(
                                "generatedInvoker.runningApplication",
                                className,
                                classLoader));
            }
        });
    }

    private void writeClassVisibility(String label, String className, ClassLoader quarkusRuntimeClassLoader) {
        Class<?> systemClass = null;
        Class<?> runtimeClass = null;
        try {
            systemClass = Class.forName(className, false, ClassLoader.getSystemClassLoader());
            out.println("  " + label + ".systemClassLoader=" + classLoaderName(systemClass.getClassLoader()));
        } catch (ReflectiveOperationException | LinkageError exception) {
            out.println("  " + label + ".systemClass=<unavailable: "
                    + exception.getClass().getSimpleName()
                    + ">");
        }
        out.println("  " + label + ".quarkusRuntimeClassLoader=" + classLoaderName(quarkusRuntimeClassLoader));
        try {
            runtimeClass = Class.forName(className, false, quarkusRuntimeClassLoader);
            out.println("  " + label + ".runtimeClassLoader=" + classLoaderName(runtimeClass.getClassLoader()));
        } catch (ReflectiveOperationException | LinkageError exception) {
            out.println("  " + label + ".runtimeClass=<unavailable: "
                    + exception.getClass().getSimpleName()
                    + ">");
        }
        if (systemClass != null && runtimeClass != null) {
            out.println("  " + label + ".sameClassObject=" + (systemClass == runtimeClass));
        }
    }

    Optional<ClassLoader> loadedClassLoader(String className, ClassLoader classLoader) {
        try {
            Class<?> loadedClass = Class.forName(className, false, classLoader);
            return Optional.ofNullable(loadedClass.getClassLoader());
        } catch (ReflectiveOperationException | LinkageError exception) {
            return Optional.empty();
        }
    }

    private void writeAlternateClassVisibility(String label, String className, ClassLoader classLoader) {
        out.println("  " + label + ".classLoader=" + classLoaderName(classLoader));
        try {
            Class<?> loadedClass = Class.forName(className, false, classLoader);
            out.println("  " + label + ".loadedClassLoader=" + classLoaderName(loadedClass.getClassLoader()));
        } catch (ReflectiveOperationException | LinkageError exception) {
            out.println("  " + label + ".loadedClass=<unavailable: "
                    + exception.getClass().getSimpleName()
                    + ">");
        }
    }

    private void writeClassLoaderShape(String label, ClassLoader classLoader) {
        out.println("  " + label + ".classLoader=" + classLoaderName(classLoader));
        if (classLoader == null) {
            return;
        }
        out.println("  " + label + ".loaderName=" + String.valueOf(classLoader.getName()));
        out.println("  " + label + ".javaParent=" + classLoaderName(classLoader.getParent()));
        quarkusParent(classLoader).ifPresent(parent -> out.println(
                "  " + label + ".quarkusParent=" + classLoaderName(parent)));
    }

    private Optional<ClassLoader> quarkusParent(ClassLoader classLoader) {
        try {
            Method parent = classLoader.getClass().getMethod("parent");
            parent.setAccessible(true);
            Object value = parent.invoke(classLoader);
            if (value instanceof ClassLoader loader) {
                return Optional.of(loader);
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    void writeQuarkusResourceElements(String label, String className, ClassLoader classLoader) {
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

    private Optional<ClassLoader> runningApplicationClassLoader() {
        try {
            Class<?> extensionClass = Class.forName("io.quarkus.test.junit.AbstractJvmQuarkusTestExtension");
            Field field = extensionClass.getDeclaredField("runningQuarkusApplication");
            field.setAccessible(true);
            Object runningApplication = field.get(null);
            if (runningApplication == null) {
                return Optional.empty();
            }
            Method getClassLoader = runningApplication.getClass().getMethod("getClassLoader");
            getClassLoader.setAccessible(true);
            Object classLoader = getClassLoader.invoke(runningApplication);
            if (classLoader instanceof ClassLoader loader) {
                return Optional.of(loader);
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    String applyModuleConfigurationToClassloader(ClassLoader quarkusRuntimeClassLoader, ClassLoader targetClassLoader) {
        if (quarkusRuntimeClassLoader == null || targetClassLoader == null) {
            return "skipped";
        }
        Optional<Object> startupAction = startupAction(quarkusRuntimeClassLoader);
        if (startupAction.isEmpty()) {
            return "<unavailable: StartupAction>";
        }
        try {
            Method apply = startupAction.get()
                    .getClass()
                    .getMethod("applyModuleConfigurationToClassloader", ClassLoader.class);
            apply.setAccessible(true);
            apply.invoke(startupAction.get(), targetClassLoader);
            return "applied";
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            String causeName = cause == null
                    ? exception.getClass().getSimpleName()
                    : cause.getClass().getSimpleName();
            return "<unavailable: " + causeName + ">";
        } catch (ReflectiveOperationException | LinkageError exception) {
            return "<unavailable: " + exception.getClass().getSimpleName() + ">";
        }
    }

    private Optional<Object> startupAction(ClassLoader classLoader) {
        try {
            Method getStartupAction = classLoader.getClass().getMethod("getStartupAction");
            getStartupAction.setAccessible(true);
            return Optional.ofNullable(getStartupAction.invoke(classLoader));
        } catch (ReflectiveOperationException | LinkageError exception) {
            return Optional.empty();
        }
    }

    private Optional<String> firstApplicationClass() {
        String mainOutputDirectory = System.getProperty(MAIN_OUTPUT_DIRECTORY_PROPERTY, "");
        if (mainOutputDirectory.isBlank()) {
            return Optional.empty();
        }
        Path outputDirectory = Path.of(mainOutputDirectory).toAbsolutePath().normalize();
        if (!java.nio.file.Files.isDirectory(outputDirectory)) {
            return Optional.empty();
        }
        try (var files = java.nio.file.Files.walk(outputDirectory)) {
            return files
                    .filter(java.nio.file.Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .map(path -> outputDirectory.relativize(path).toString().replace(java.io.File.separatorChar, '.'))
                    .map(path -> path.substring(0, path.length() - ".class".length()))
                    .filter(className -> !"module-info".equals(className))
                    .sorted()
                    .findFirst();
        } catch (java.io.IOException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> failingClassName(Throwable failure) {
        if (!(failure instanceof ClassNotFoundException || failure instanceof NoClassDefFoundError)) {
            return Optional.empty();
        }
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(message.replace('/', '.'));
    }

    private static String classLoaderName(ClassLoader classLoader) {
        if (classLoader == null) {
            return "<bootstrap>";
        }
        return classLoader.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(classLoader));
    }

    private void writeBuildChainCustomizerDiagnostic(ClassLoader quarkusRuntimeClassLoader) {
        out.println("Zolt Quarkus build-chain diagnostic:");
        writeBuildChainCustomizerDiagnostic("system", ClassLoader.getSystemClassLoader());
        if (quarkusRuntimeClassLoader != null
                && quarkusRuntimeClassLoader != ClassLoader.getSystemClassLoader()) {
            writeBuildChainCustomizerDiagnostic("quarkusRuntime", quarkusRuntimeClassLoader);
        }
    }

    private void writeBuildChainCustomizerDiagnostic(String label, ClassLoader classLoader) {
        out.println("  " + label + "Loader=" + classLoaderName(classLoader));
        try {
            Class<?> buildChainFunction = Class.forName(TEST_BUILD_CHAIN_FUNCTION, false, classLoader);
            ClassLoader buildChainLoader = buildChainFunction.getClassLoader();
            out.println("    TestBuildChainFunction.loader=" + classLoaderName(buildChainLoader));
            out.println("    serviceResources=" + serviceResources(buildChainLoader));
            out.println("    providers=" + serviceProviders(buildChainLoader));
        } catch (ReflectiveOperationException | LinkageError exception) {
            out.println("    TestBuildChainFunction=<unavailable: "
                    + exception.getClass().getSimpleName()
                    + ">");
        }
    }

    private List<String> serviceResources(ClassLoader classLoader) {
        try {
            Enumeration<URL> resources = classLoader.getResources(TEST_BUILD_CHAIN_CUSTOMIZER_SERVICE);
            List<String> urls = new ArrayList<>();
            while (resources.hasMoreElements()) {
                urls.add(resources.nextElement().toString());
            }
            return urls.isEmpty() ? List.of("<none>") : List.copyOf(urls);
        } catch (java.io.IOException exception) {
            return List.of("<unavailable: " + exception.getClass().getSimpleName() + ">");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<String> serviceProviders(ClassLoader classLoader) {
        try {
            Class<?> spi = Class.forName(TEST_BUILD_CHAIN_CUSTOMIZER_SPI, false, classLoader);
            ServiceLoader<?> serviceLoader = ServiceLoader.load((Class) spi, classLoader);
            List<String> providers = new ArrayList<>();
            for (Object provider : serviceLoader) {
                providers.add(provider.getClass().getName()
                        + "@"
                        + classLoaderName(provider.getClass().getClassLoader()));
            }
            return providers.isEmpty() ? List.of("<none>") : List.copyOf(providers);
        } catch (ReflectiveOperationException | LinkageError | ServiceConfigurationError exception) {
            return List.of("<unavailable: " + exception.getClass().getSimpleName() + ">");
        }
    }

}
