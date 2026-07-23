package sh.zolt.build;

import sh.zolt.build.compile.JavacRunner;
import sh.zolt.classpath.Classpath;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Builds a tiny, committed-pattern jvm exec tool jar for end-to-end exec tests: its main class writes
 * {@code args[1]} to {@code $ZOLT_OUTPUT_DIR/args[0]}, so the same jar can drive the java-sources and
 * resources lanes with no network.
 */
public final class ExecToolJarFixture {
    public static final String MAIN_CLASS = "com.example.tool.GenTool";

    private ExecToolJarFixture() {
    }

    public static Path generatorJar(Path workDirectory) throws IOException {
        Path toolSource = source(workDirectory, "exec-tool-src/com/example/tool/GenTool.java", """
                package com.example.tool;

                import java.nio.file.Files;
                import java.nio.file.Path;

                public final class GenTool {
                    public static void main(String[] args) throws Exception {
                        Path target = Path.of(System.getenv("ZOLT_OUTPUT_DIR")).resolve(args[0]);
                        Files.createDirectories(target.getParent());
                        Files.writeString(target, args[1]);
                    }
                }
                """);
        Path classes = workDirectory.resolve("exec-tool-classes");
        new JavacRunner().compile(currentJavac(), List.of(toolSource), new Classpath(List.of()), classes);
        Path jar = workDirectory.resolve("gen-tool.jar");
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
            List<Path> files = paths.filter(Files::isRegularFile).sorted().toList();
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
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? name + ".exe" : name;
    }
}
