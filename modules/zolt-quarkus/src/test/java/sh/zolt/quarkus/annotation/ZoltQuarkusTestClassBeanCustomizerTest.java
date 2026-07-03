package sh.zolt.quarkus.annotation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.quarkus.annotation.bridge.QuarkusAdditionalApplicationArchiveBuildItemBridge;
import sh.zolt.quarkus.annotation.bridge.QuarkusAdditionalBeanBuildItemBridge;
import sh.zolt.quarkus.annotation.diagnostic.QuarkusContextClassLoaderDiagnostic;
import sh.zolt.quarkus.testsupport.QuarkusProvidedRuntime;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ZoltQuarkusTestClassBeanCustomizerTest {
    private static final String DIAGNOSTIC_FILE_PROPERTY =
            "zolt.quarkus.test-class-bean-diagnostic-file";

    @TempDir
    private Path tempDir;

    @Test
    void treatsCompiledMainOutputClassesAsApplicationClasses() throws Exception {
        Path outputDirectory = tempDir.resolve("target/classes");
        Path classFile = outputDirectory.resolve("com/example/HelloResource.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, new byte[] {0});
        String previous = System.getProperty(QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY);
        try {
            System.setProperty(
                    QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY,
                    outputDirectory.toString());

            assertTrue(ZoltQuarkusApplicationClassPredicate.test("com.example.HelloResource"));
            assertFalse(ZoltQuarkusApplicationClassPredicate.test("com.example.MissingResource"));
        } finally {
            restoreMainOutputDirectory(previous);
        }
    }

    @Test
    void treatsCompiledTestOutputClassesAsApplicationClasses() throws Exception {
        Path outputDirectory = tempDir.resolve("target/test-classes");
        Path classFile = outputDirectory.resolve("com/example/HelloResourceQuarkusTest.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, new byte[] {0});
        String previous = System.getProperty(QuarkusAnnotationProgrammaticRunner.TEST_OUTPUT_DIRECTORY_PROPERTY);
        try {
            System.setProperty(
                    QuarkusAnnotationProgrammaticRunner.TEST_OUTPUT_DIRECTORY_PROPERTY,
                    outputDirectory.toString());

            assertTrue(ZoltQuarkusApplicationClassPredicate.test("com.example.HelloResourceQuarkusTest"));
            assertFalse(ZoltQuarkusApplicationClassPredicate.test("com.example.MissingResourceQuarkusTest"));
        } finally {
            restoreTestOutputDirectory(previous);
        }
    }

    @Test
    void ignoresClassesWhenMainOutputDirectoryIsUnavailable() {
        String previous = System.getProperty(QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY);
        try {
            System.clearProperty(QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY);

            assertFalse(ZoltQuarkusApplicationClassPredicate.test("com.example.HelloResource"));
        } finally {
            restoreMainOutputDirectory(previous);
        }
    }

    @Test
    void marksAdditionalBeanBuilderUnremovable() throws Exception {
        FakeAdditionalBeanBuilder builder = new FakeAdditionalBeanBuilder();

        QuarkusAdditionalBeanBuildItemBridge.markBuilderUnremovable(builder);

        assertFalse(builder.removable);
    }

    @Test
    void setsAdditionalBeanBuilderDefaultScope() throws Exception {
        FakeAdditionalBeanBuilder builder = new FakeAdditionalBeanBuilder();

        QuarkusAdditionalBeanBuildItemBridge.setBuilderDefaultScope(
                builder,
                "jakarta.enterprise.context.Dependent");

        assertTrue(builder.defaultScope.contains("jakarta.enterprise.context.Dependent"));
    }

    @Test
    void recordsContextClassLoaderVisibilityForSelectedClasses() {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            String diagnostic = QuarkusContextClassLoaderDiagnostic.formatSelectedClasses(
                    List.of("java.lang.String", "com.example.MissingQuarkusTest"));

            assertTrue(diagnostic.contains("java.lang.String[loadable="), diagnostic);
            assertTrue(diagnostic.contains("com.example.MissingQuarkusTest[loadable=false"), diagnostic);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test
    void readsExistingTestOutputDirectoryForApplicationArchiveHandoff() throws Exception {
        Path outputDirectory = tempDir.resolve("target/test-classes");
        Files.createDirectories(outputDirectory);
        String previous = System.getProperty(QuarkusAnnotationProgrammaticRunner.TEST_OUTPUT_DIRECTORY_PROPERTY);
        try {
            System.setProperty(
                    QuarkusAnnotationProgrammaticRunner.TEST_OUTPUT_DIRECTORY_PROPERTY,
                    outputDirectory.toString());

            assertTrue(QuarkusAdditionalApplicationArchiveBuildItemBridge.testOutputDirectory().isPresent());
        } finally {
            restoreTestOutputDirectory(previous);
        }
    }

    @Test
    void ignoresMissingTestOutputDirectoryForApplicationArchiveHandoff() {
        String previous = System.getProperty(QuarkusAnnotationProgrammaticRunner.TEST_OUTPUT_DIRECTORY_PROPERTY);
        try {
            System.setProperty(
                    QuarkusAnnotationProgrammaticRunner.TEST_OUTPUT_DIRECTORY_PROPERTY,
                    tempDir.resolve("missing-test-classes").toString());

            assertTrue(QuarkusAdditionalApplicationArchiveBuildItemBridge.testOutputDirectory().isEmpty());
        } finally {
            restoreTestOutputDirectory(previous);
        }
    }

    @Test
    void writesProducerAndConsumerDiagnosticsWhenNoQuarkusTestsAreSelected() throws Exception {
        Path diagnosticFile = tempDir.resolve("diagnostics/customizer.txt");
        String previousDiagnostic = System.getProperty(DIAGNOSTIC_FILE_PROPERTY);
        try (URLClassLoader runtime = QuarkusProvidedRuntime.open()) {
            System.setProperty(DIAGNOSTIC_FILE_PROPERTY, diagnosticFile.toString());

            producerWithNullIndex(runtime).accept(buildChainBuilder(runtime));
        } finally {
            restore(DIAGNOSTIC_FILE_PROPERTY, previousDiagnostic);
        }

        String diagnostic = Files.readString(diagnosticFile);
        assertTrue(diagnostic.contains("producer.invoked=true"), diagnostic);
        assertTrue(diagnostic.contains("producer.indexClass=<null>"), diagnostic);
        assertTrue(diagnostic.contains("producer.candidates=<none>"), diagnostic);
        assertTrue(diagnostic.contains("producer.indexSelectedClasses=<missing>"), diagnostic);
        assertTrue(diagnostic.contains("consumer.accepted=true"), diagnostic);
        assertTrue(diagnostic.contains("consumer.candidates=<none>"), diagnostic);
    }

    @Test
    void formatsBootstrapClassLoaderName() throws Exception {
        try (URLClassLoader runtime = QuarkusProvidedRuntime.open()) {
            Method classLoaderName = customizerClass(runtime).getMethod("classLoaderName", ClassLoader.class);

            assertEquals("<bootstrap>", classLoaderName.invoke(null, new Object[] {null}));
        }
    }

    public static final class FakeAdditionalBeanBuilder {
        private boolean removable = true;
        private String defaultScope = "";

        public FakeAdditionalBeanBuilder setUnremovable() {
            removable = false;
            return this;
        }

        public FakeAdditionalBeanBuilder setDefaultScope(Object defaultScope) {
            this.defaultScope = defaultScope.toString();
            return this;
        }
    }

    private static void restoreMainOutputDirectory(String previous) {
        if (previous == null) {
            System.clearProperty(QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY);
            return;
        }
        System.setProperty(QuarkusAnnotationProgrammaticRunner.MAIN_OUTPUT_DIRECTORY_PROPERTY, previous);
    }

    private static void restoreTestOutputDirectory(String previous) {
        if (previous == null) {
            System.clearProperty(QuarkusAnnotationProgrammaticRunner.TEST_OUTPUT_DIRECTORY_PROPERTY);
            return;
        }
        System.setProperty(QuarkusAnnotationProgrammaticRunner.TEST_OUTPUT_DIRECTORY_PROPERTY, previous);
    }

    private static void restore(String property, String previous) {
        if (previous == null) {
            System.clearProperty(property);
            return;
        }
        System.setProperty(property, previous);
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> producerWithNullIndex(URLClassLoader runtime) throws Exception {
        Class<?> indexClass = Class.forName("org.jboss.jandex.Index", false, runtime);
        Method produce = customizerClass(runtime).getMethod("produce", indexClass);
        Object customizer = customizerClass(runtime).getConstructor().newInstance();
        return (Consumer<Object>) produce.invoke(customizer, new Object[] {null});
    }

    private static Object buildChainBuilder(URLClassLoader runtime) throws Exception {
        return Class.forName("io.quarkus.builder.BuildChain", false, runtime)
                .getMethod("builder")
                .invoke(null);
    }

    private static Class<?> customizerClass(URLClassLoader runtime) throws ClassNotFoundException {
        return Class.forName("sh.zolt.quarkus.annotation.ZoltQuarkusTestClassBeanCustomizer", false, runtime);
    }
}
