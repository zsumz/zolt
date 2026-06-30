package com.zolt.build;

import com.zolt.build.compile.JavacRunner;
import com.zolt.classpath.Classpath;
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
