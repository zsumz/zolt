package sh.zolt.javac;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Shared helpers for worker attribution tests: a synthetic isolating processor and response parsing. */
final class WorkerAttributionFixture {
    private WorkerAttributionFixture() {
    }

    /**
     * Compiles a processor that generates a {@code gen.<Name>Info} source for each root class, passing
     * the class as the originating element unless {@code -Azolt.omitOriginating=true} is set.
     */
    static Path compileProcessor(Path tempDir) throws Exception {
        return compile(tempDir, "GenProc", """
                package proc;
                import java.io.Writer;
                import java.util.Set;
                import javax.annotation.processing.AbstractProcessor;
                import javax.annotation.processing.RoundEnvironment;
                import javax.annotation.processing.SupportedAnnotationTypes;
                import javax.lang.model.SourceVersion;
                import javax.lang.model.element.Element;
                import javax.lang.model.element.ElementKind;
                import javax.lang.model.element.TypeElement;

                @SupportedAnnotationTypes("*")
                public class GenProc extends AbstractProcessor {
                    private boolean done;
                    @Override public SourceVersion getSupportedSourceVersion() { return SourceVersion.latestSupported(); }
                    @Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        if (done) { return false; }
                        boolean omit = "true".equals(processingEnv.getOptions().get("zolt.omitOriginating"));
                        for (Element element : roundEnv.getRootElements()) {
                            if (element.getKind() != ElementKind.CLASS) { continue; }
                            String generatedName = "gen." + element.getSimpleName() + "Info";
                            try {
                                Writer writer = (omit
                                        ? processingEnv.getFiler().createSourceFile(generatedName)
                                        : processingEnv.getFiler().createSourceFile(generatedName, element)).openWriter();
                                writer.write("package gen; public class " + element.getSimpleName() + "Info {}");
                                writer.close();
                                done = true;
                            } catch (Exception exception) { throw new RuntimeException(exception); }
                        }
                        return false;
                    }
                }
                """);
    }

    /**
     * Compiles a processor that generates a {@code gen/<Name>.meta} resource under {@code CLASS_OUTPUT}
     * for each root class, passing the class as the originating element unless
     * {@code -Azolt.omitOriginating=true} is set. Mirrors {@link #compileProcessor} for the resource path.
     */
    static Path compileResourceProcessor(Path tempDir) throws Exception {
        return compile(tempDir, "GenResourceProc", """
                package proc;
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

                @SupportedAnnotationTypes("*")
                public class GenResourceProc extends AbstractProcessor {
                    private boolean done;
                    @Override public SourceVersion getSupportedSourceVersion() { return SourceVersion.latestSupported(); }
                    @Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        if (done) { return false; }
                        boolean omit = "true".equals(processingEnv.getOptions().get("zolt.omitOriginating"));
                        for (Element element : roundEnv.getRootElements()) {
                            if (element.getKind() != ElementKind.CLASS) { continue; }
                            String resourceName = element.getSimpleName() + ".meta";
                            try {
                                FileObject file = omit
                                        ? processingEnv.getFiler().createResource(
                                                StandardLocation.CLASS_OUTPUT, "gen", resourceName)
                                        : processingEnv.getFiler().createResource(
                                                StandardLocation.CLASS_OUTPUT, "gen", resourceName, element);
                                Writer writer = file.openWriter();
                                writer.write("owner=" + element.getSimpleName());
                                writer.close();
                                done = true;
                            } catch (Exception exception) { throw new RuntimeException(exception); }
                        }
                        return false;
                    }
                }
                """);
    }

    private static Path compile(Path tempDir, String simpleName, String source) throws Exception {
        Path processorSource = tempDir.resolve("proc/" + simpleName + ".java");
        Files.createDirectories(processorSource.getParent());
        Files.writeString(processorSource, source);
        Path processorClasses = Files.createDirectories(tempDir.resolve("processor-classes"));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int exitCode = compiler.run(
                null, diagnostics, diagnostics, "-d", processorClasses.toString(), processorSource.toString());
        assertEquals(0, exitCode, diagnostics.toString(StandardCharsets.UTF_8));
        Path services = processorClasses.resolve("META-INF/services/javax.annotation.processing.Processor");
        Files.createDirectories(services.getParent());
        Files.writeString(services, "proc." + simpleName + "\n");
        return processorClasses;
    }

    static Attribution parse(byte[] responseBytes) throws Exception {
        try (DataInputStream response = new DataInputStream(new java.io.ByteArrayInputStream(responseBytes))) {
            int exitCode = response.readInt();
            int diagnosticsLength = response.readInt();
            String diagnostics = new String(response.readNBytes(diagnosticsLength), StandardCharsets.UTF_8);
            boolean present = response.readInt() == 1;
            boolean unattributed = false;
            List<Entry> entries = new ArrayList<>();
            if (present) {
                unattributed = response.readInt() == 1;
                int entryCount = response.readInt();
                for (int index = 0; index < entryCount; index++) {
                    String path = readString(response);
                    int kind = response.readInt();
                    String createdType = readString(response);
                    int originatingCount = response.readInt();
                    List<String> originating = new ArrayList<>();
                    for (int origin = 0; origin < originatingCount; origin++) {
                        originating.add(readString(response));
                    }
                    entries.add(new Entry(path, kind, createdType, originating));
                }
            }
            return new Attribution(exitCode, diagnostics, present, unattributed, entries);
        }
    }

    private static String readString(DataInputStream input) throws Exception {
        int length = input.readInt();
        return new String(input.readNBytes(length), StandardCharsets.UTF_8);
    }

    record Attribution(
            int exitCode, String diagnostics, boolean present, boolean unattributed, List<Entry> entries) {
    }

    record Entry(String path, int kind, String createdType, List<String> originatingTypes) {
    }
}
