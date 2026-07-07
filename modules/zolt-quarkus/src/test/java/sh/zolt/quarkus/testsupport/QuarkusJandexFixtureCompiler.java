package sh.zolt.quarkus.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public final class QuarkusJandexFixtureCompiler {
    private static final List<String> PROVIDED_JARS = List.of(
            "io/quarkus/quarkus-junit/3.33.2/quarkus-junit-3.33.2.jar",
            "io/quarkus/quarkus-test-common/3.33.2/quarkus-test-common-3.33.2.jar",
            "io/smallrye/jandex/3.5.3/jandex-3.5.3.jar");

    private QuarkusJandexFixtureCompiler() {
    }

    public static Path compile(Path outputDirectory, Map<String, String> sources) throws IOException {
        Files.createDirectories(outputDirectory);
        List<Path> sourceFiles = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path sourceFile = outputDirectory.resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(sourceFile.getParent());
            Files.writeString(sourceFile, source.getValue());
            sourceFiles.add(sourceFile);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new AssertionError("JDK compiler is required for Quarkus Jandex fixture tests.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            List<String> options = List.of(
                    "-classpath",
                    compilerClasspath(),
                    "-d",
                    outputDirectory.toString());
            Boolean compiled = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits)
                    .call();
            if (!Boolean.TRUE.equals(compiled)) {
                throw new AssertionError("Could not compile Quarkus fixture sources: "
                        + diagnostics.getDiagnostics());
            }
        }
        return outputDirectory;
    }

    public static FixtureRuntime fixtureRuntime() throws IOException {
        List<java.net.URL> urls = new ArrayList<>();
        Path repoRoot = repoRoot();
        try (Stream<Path> paths = Files.walk(repoRoot.resolve("modules"), 3)) {
            for (Path classesDirectory : paths
                    .filter(Files::isDirectory)
                    .filter(path -> path.endsWith("target/classes"))
                    .sorted()
                    .toList()) {
                urls.add(QuarkusTestRuntimeClasspath.url(classesDirectory));
            }
        }
        urls.addAll(QuarkusTestRuntimeClasspath.currentJvmUrls());
        for (Path jar : repoCacheJars(repoRoot)) {
            urls.add(QuarkusTestRuntimeClasspath.url(jar));
        }
        return new FixtureRuntime(new URLClassLoader(
                urls.toArray(java.net.URL[]::new),
                ClassLoader.getPlatformClassLoader()));
    }

    private static String compilerClasspath() {
        List<String> entries = new ArrayList<>();
        entries.add(System.getProperty("java.class.path"));
        for (Path jar : repoCacheJars(repoRoot())) {
            entries.add(jar.toString());
        }
        return String.join(java.io.File.pathSeparator, entries);
    }

    private static List<Path> repoCacheJars(Path repoRoot) {
        return QuarkusTestRuntimeClasspath.existingRepoCacheJars(repoRoot, PROVIDED_JARS);
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("zolt.lock"))
                    && Files.exists(current.resolve("modules/zolt-quarkus"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Could not locate Zolt repository root for Quarkus fixture tests.");
    }

    public static final class FixtureRuntime implements AutoCloseable {
        private final URLClassLoader classLoader;

        private FixtureRuntime(URLClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        public Class<?> loadClass(String className) throws ClassNotFoundException {
            return Class.forName(className, true, classLoader);
        }

        public Object newInstance(String className) throws ReflectiveOperationException {
            java.lang.reflect.Constructor<?> constructor = loadClass(className).getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }

        public Object index(Path outputDirectory) throws ReflectiveOperationException, IOException {
            Class<?> indexerClass = loadClass("org.jboss.jandex.Indexer");
            Object indexer = indexerClass.getConstructor().newInstance();
            try (Stream<Path> paths = Files.walk(outputDirectory)) {
                for (Path classFile : paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".class"))
                        .sorted()
                        .toList()) {
                    try (InputStream input = Files.newInputStream(classFile)) {
                        indexerClass.getMethod("index", InputStream.class).invoke(indexer, input);
                    }
                }
            }
            return indexerClass.getMethod("complete").invoke(indexer);
        }

        public Object emptyIndex() throws ReflectiveOperationException {
            Class<?> indexerClass = loadClass("org.jboss.jandex.Indexer");
            Object indexer = indexerClass.getConstructor().newInstance();
            return indexerClass.getMethod("complete").invoke(indexer);
        }

        @Override
        public void close() throws IOException {
            classLoader.close();
        }
    }
}
