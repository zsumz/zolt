package sh.zolt.build;

import sh.zolt.build.compile.JavacRunner;
import sh.zolt.classpath.Classpath;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

public final class AnnotationProcessorFixture {
    private AnnotationProcessorFixture() {
    }

    public static Path processorJar(Path workDirectory) throws IOException {
        Path processorSource = source(workDirectory, "processor-src/com/example/processor/GeneratingProcessor.java", """
                package com.example.processor;

                import java.io.IOException;
                import java.io.Writer;
                import java.util.Set;
                import javax.annotation.processing.AbstractProcessor;
                import javax.annotation.processing.RoundEnvironment;
                import javax.annotation.processing.SupportedAnnotationTypes;
                import javax.lang.model.SourceVersion;
                import javax.lang.model.element.TypeElement;
                import javax.tools.JavaFileObject;

                @SupportedAnnotationTypes("*")
                public final class GeneratingProcessor extends AbstractProcessor {
                    private boolean generated;

                    @Override
                    public SourceVersion getSupportedSourceVersion() {
                        return SourceVersion.latestSupported();
                    }

                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        if (generated || roundEnv.processingOver()) {
                            return false;
                        }
                        try {
                            JavaFileObject source = processingEnv.getFiler()
                                    .createSourceFile("com.example.GeneratedMessage");
                            try (Writer writer = source.openWriter()) {
                                writer.write("package com.example; "
                                        + "public final class GeneratedMessage { "
                                        + "public static String value() { return \\"generated\\"; } "
                                        + "}");
                            }
                            generated = true;
                            return false;
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    }
                }
                """);
        Path classes = workDirectory.resolve("processor-classes");
        new JavacRunner().compile(
                currentJavac(),
                List.of(processorSource),
                new Classpath(List.of()),
                classes);
        Path serviceFile = classes.resolve("META-INF/services/javax.annotation.processing.Processor");
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, "com.example.processor.GeneratingProcessor\n");
        Path jar = workDirectory.resolve("processor.jar");
        writeJar(classes, jar);
        return jar;
    }

    /**
     * An isolating processor that generates a {@code <Name>Meta} source for each {@code @com.example.Tracked}
     * class, passing the annotated type as the originating element so its outputs can be attributed.
     */
    public static Path attributingProcessorJar(Path workDirectory) throws IOException {
        return trackingProcessorJar(workDirectory, true);
    }

    /** The same isolating processor without originating elements, so its outputs cannot be attributed. */
    public static Path nonOriginatingProcessorJar(Path workDirectory) throws IOException {
        return trackingProcessorJar(workDirectory, false);
    }

    /**
     * An isolating processor that generates a {@code meta/<Name>.properties} resource under
     * {@code CLASS_OUTPUT} for each {@code @com.example.Tracked} class, passing the annotated type as the
     * originating element so the resource can be attributed.
     */
    public static Path attributingResourceProcessorJar(Path workDirectory) throws IOException {
        return trackingResourceProcessorJar(workDirectory, true);
    }

    /** The same isolating resource processor without originating elements, so it cannot be attributed. */
    public static Path nonOriginatingResourceProcessorJar(Path workDirectory) throws IOException {
        return trackingResourceProcessorJar(workDirectory, false);
    }

    private static Path trackingProcessorJar(Path workDirectory, boolean withOriginating) throws IOException {
        String createCall = withOriginating
                ? "processingEnv.getFiler().createSourceFile(generatedName, type)"
                : "processingEnv.getFiler().createSourceFile(generatedName)";
        return buildTrackingJar(
                workDirectory,
                "TrackingProcessor",
                TRACKING_PROCESSOR_SOURCE.replace("__CREATE_CALL__", createCall));
    }

    private static Path trackingResourceProcessorJar(Path workDirectory, boolean withOriginating) throws IOException {
        String createCall = withOriginating
                ? "processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, \"meta\", simple + \".properties\", type)"
                : "processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, \"meta\", simple + \".properties\")";
        return buildTrackingJar(
                workDirectory,
                "TrackingResourceProcessor",
                TRACKING_RESOURCE_PROCESSOR_SOURCE.replace("__CREATE_CALL__", createCall));
    }

    private static Path buildTrackingJar(
            Path workDirectory, String simpleName, String processorSource) throws IOException {
        Path source = source(
                workDirectory,
                "tracking-src/com/example/processor/" + simpleName + ".java",
                processorSource);
        Path classes = workDirectory.resolve("tracking-classes");
        new JavacRunner().compile(currentJavac(), List.of(source), new Classpath(List.of()), classes);
        Path serviceFile = classes.resolve("META-INF/services/javax.annotation.processing.Processor");
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, "com.example.processor." + simpleName + "\n");
        Path incrementalFile = classes.resolve("META-INF/gradle/incremental.annotation.processors");
        Files.createDirectories(incrementalFile.getParent());
        Files.writeString(incrementalFile, "com.example.processor." + simpleName + ",isolating\n");
        Path jar = workDirectory.resolve("tracking-processor.jar");
        writeJar(classes, jar);
        return jar;
    }

    private static final String TRACKING_PROCESSOR_SOURCE = """
            package com.example.processor;

            import java.io.Writer;
            import java.util.Set;
            import javax.annotation.processing.AbstractProcessor;
            import javax.annotation.processing.RoundEnvironment;
            import javax.annotation.processing.SupportedAnnotationTypes;
            import javax.lang.model.SourceVersion;
            import javax.lang.model.element.Element;
            import javax.lang.model.element.ElementKind;
            import javax.lang.model.element.TypeElement;
            import javax.tools.JavaFileObject;

            @SupportedAnnotationTypes("com.example.Tracked")
            public final class TrackingProcessor extends AbstractProcessor {
                @Override
                public SourceVersion getSupportedSourceVersion() {
                    return SourceVersion.latestSupported();
                }

                @Override
                public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                    TypeElement tracked = processingEnv.getElementUtils().getTypeElement("com.example.Tracked");
                    if (tracked == null) {
                        return false;
                    }
                    for (Element element : roundEnv.getElementsAnnotatedWith(tracked)) {
                        if (element.getKind() != ElementKind.CLASS) {
                            continue;
                        }
                        TypeElement type = (TypeElement) element;
                        String pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
                        String simple = type.getSimpleName().toString();
                        String generatedName = (pkg.isEmpty() ? "" : pkg + ".") + simple + "Meta";
                        try {
                            JavaFileObject file = __CREATE_CALL__;
                            try (Writer writer = file.openWriter()) {
                                writer.write("package " + pkg + "; public final class " + simple + "Meta { "
                                        + "public static String owner() { return \\"" + simple + "\\"; } }");
                            }
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    }
                    return false;
                }
            }
            """;

    private static final String TRACKING_RESOURCE_PROCESSOR_SOURCE = """
            package com.example.processor;

            import java.io.Writer;
            import java.util.Set;
            import javax.annotation.processing.AbstractProcessor;
            import javax.annotation.processing.RoundEnvironment;
            import javax.annotation.processing.SupportedAnnotationTypes;
            import javax.lang.model.SourceVersion;
            import javax.lang.model.element.Element;
            import javax.lang.model.element.ElementKind;
            import javax.lang.model.element.TypeElement;
            import javax.tools.FileObject;
            import javax.tools.StandardLocation;

            @SupportedAnnotationTypes("com.example.Tracked")
            public final class TrackingResourceProcessor extends AbstractProcessor {
                @Override
                public SourceVersion getSupportedSourceVersion() {
                    return SourceVersion.latestSupported();
                }

                @Override
                public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                    TypeElement tracked = processingEnv.getElementUtils().getTypeElement("com.example.Tracked");
                    if (tracked == null) {
                        return false;
                    }
                    for (Element element : roundEnv.getElementsAnnotatedWith(tracked)) {
                        if (element.getKind() != ElementKind.CLASS) {
                            continue;
                        }
                        TypeElement type = (TypeElement) element;
                        String simple = type.getSimpleName().toString();
                        try {
                            FileObject file = __CREATE_CALL__;
                            try (Writer writer = file.openWriter()) {
                                writer.write("owner=" + simple);
                            }
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    }
                    return false;
                }
            }
            """;

    private static Path source(Path root, String path, String content) throws IOException {
        Path source = root.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
        return source;
    }

    private static void writeJar(Path root, Path jar) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
                Stream<Path> paths = Files.walk(root)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
            for (Path file : files) {
                JarEntry entry = new JarEntry(root.relativize(file).toString().replace(File.separatorChar, '/'));
                output.putNextEntry(entry);
                output.write(Files.readAllBytes(file));
                output.closeEntry();
            }
        }
    }

    private static Path currentJavac() {
        return Path.of(System.getProperty("java.home")).resolve("bin").resolve(executable("javac"));
    }

    private static String executable(String name) {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? name + ".exe"
                : name;
    }
}
