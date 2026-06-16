package com.zolt.build;

import static com.zolt.build.PackageServiceTestSupport.buildSettingsWithMetadata;
import static com.zolt.build.PackageServiceTestSupport.config;
import static com.zolt.build.PackageServiceTestSupport.createJarWithEntry;
import static com.zolt.build.PackageServiceTestSupport.createSymlink;
import static com.zolt.build.PackageServiceTestSupport.currentJavaMajorVersion;
import static com.zolt.build.PackageServiceTestSupport.readEntry;
import static com.zolt.build.PackageServiceTestSupport.resourceFilteringSettings;
import static com.zolt.build.PackageServiceTestSupport.source;
import static com.zolt.build.PackageServiceTestSupport.writeLockfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.lockfile.LockfileReadException;
import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.BuildSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.ProjectPathException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageServiceTest {
    private final PackageService packageService = new PackageService();

    @TempDir
    private Path projectDir;

    @Test
    void packagesCompiledClassesWithGeneratedManifest() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);

        PackageResult result = packageService.packageJar(
                projectDir,
                config(Optional.of("com.example.Main")),
                projectDir.resolve("cache"));

        assertEquals(projectDir.resolve("target/demo-0.1.0.jar"), result.jarPath());
        assertEquals(PackageMode.THIN, result.mode());
        assertEquals(1, result.entryCount());
        assertTrue(result.hasMainClass());
        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            assertNotNull(jar.getEntry("META-INF/MANIFEST.MF"));
            assertNotNull(jar.getEntry("com/example/Main.class"));
            assertNull(jar.getEntry(".zolt-build-main.fingerprint"));
            assertNull(jar.getEntry(".zolt-build-main.fingerprint.state"));
            assertNull(jar.getEntry(".zolt-incremental-main.state"));
            Attributes attributes = jar.getManifest().getMainAttributes();
            assertEquals("1.0", attributes.getValue(Attributes.Name.MANIFEST_VERSION));
            assertEquals("com.example.Main", attributes.getValue(Attributes.Name.MAIN_CLASS));
        }
    }

    @Test
    void rejectsPackageArchiveNameThatUsesUnsafeProjectName() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);

        ProjectPathException exception = assertThrows(
                ProjectPathException.class,
                () -> packageService.packageJar(
                        projectDir,
                        config(new ProjectMetadata(
                                "../outside",
                                "0.1.0",
                                "com.example",
                                currentJavaMajorVersion(),
                                Optional.of("com.example.Main"))),
                        projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("[project].name"));
        assertTrue(exception.getMessage().contains("../outside"));
        assertFalse(Files.exists(projectDir.resolve("target/outside-0.1.0.jar")));
    }

    @Test
    void rejectsPackageArchiveWhenTargetParentSymlinkEscapesProject() throws IOException {
        writeLockfile(projectDir);
        Path outside = Files.createTempDirectory(projectDir.getParent(), "outside-target-");
        createSymlink(projectDir.resolve("target"), outside);
        Path classes = projectDir.resolve("classes/com/example/Main.class");
        Files.createDirectories(classes.getParent());
        Files.write(classes, new byte[] {0});
        BuildResult buildResult = new BuildResult(Optional.empty(), 1, 0, projectDir.resolve("classes"), "");

        ProjectPathException exception = assertThrows(
                ProjectPathException.class,
                () -> packageService.packageJar(
                        projectDir,
                        config(Optional.of("com.example.Main")),
                        buildResult));

        assertTrue(exception.getMessage().contains("package archive"));
        assertTrue(exception.getMessage().contains("target/demo-0.1.0.jar"));
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
        assertFalse(Files.exists(outside.resolve("demo-0.1.0.jar")));
    }

    @Test
    void packagesCustomManifestAttributesInThinJar() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withPackageSettings(new PackageSettings(
                        PackageMode.THIN,
                        false,
                        false,
                        false,
                        null,
                        Map.of(
                                "Automatic-Module-Name", "com.example.demo",
                                "Bundle-SymbolicName", "com.example.demo")));

        PackageResult result = packageService.packageJar(projectDir, config, projectDir.resolve("cache"));

        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            Attributes attributes = jar.getManifest().getMainAttributes();
            assertEquals("com.example.demo", attributes.getValue("Automatic-Module-Name"));
            assertEquals("com.example.demo", attributes.getValue("Bundle-SymbolicName"));
            assertEquals("com.example.Main", attributes.getValue(Attributes.Name.MAIN_CLASS));
        }
    }

    @Test
    void failsBeforeWritingPackageWhenCachedRuntimeJarDoesNotMatchLockfileHash() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path runtimeJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Files.createDirectories(runtimeJar.getParent());
        Files.writeString(runtimeJar, "corrupted runtime jar bytes");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = true
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                jarSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
                dependencies = []
                """);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);

        LockfileReadException exception = assertThrows(
                LockfileReadException.class,
                () -> packageService.packageJar(
                        projectDir,
                        config(Optional.of("com.example.Main")),
                        cacheRoot));

        assertTrue(exception.getMessage().contains(
                "Cached jar integrity check failed for com.example:runtime-lib:1.0.0"));
        assertFalse(Files.exists(projectDir.resolve("target/demo-0.1.0.jar")));
    }

    @Test
    void packagesResourcesAndNativeImageConfigDeterministically() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        source(projectDir, "src/main/resources/config/app.properties", "name=demo\n");
        source(projectDir, "src/main/resources/META-INF/native-image/reflect-config.json", "[]\n");

        PackageResult result = packageService.packageJar(
                projectDir,
                config(Optional.of("com.example.Main")),
                projectDir.resolve("cache"));

        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            assertEquals(List.of(
                    "META-INF/MANIFEST.MF",
                    "META-INF/native-image/reflect-config.json",
                    "com/example/Main.class",
                    "config/app.properties"), jar.stream().map(JarEntry::getName).toList());
            assertEquals("[]\n", readEntry(jar, "META-INF/native-image/reflect-config.json"));
            assertEquals("name=demo\n", readEntry(jar, "config/app.properties"));
        }
    }

    @Test
    void packagesFilteredResourcesInThinJar() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        source(projectDir, "src/main/resources/application.properties", "name=@projectName@\nversion=@projectVersion@\n");
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withBuildSettings(BuildSettings.defaults().withResourceFiltering(resourceFilteringSettings()));

        PackageResult result = packageService.packageJar(projectDir, config, projectDir.resolve("cache"));

        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            assertEquals("name=demo\nversion=0.1.0\n", readEntry(jar, "application.properties"));
        }
    }

    @Test
    void packagesGeneratedBuildInfoMetadata() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withBuildSettings(buildSettingsWithMetadata(new BuildMetadataSettings(true, false, true)));

        PackageResult result = packageService.packageJar(projectDir, config, projectDir.resolve("cache"));

        assertEquals(2, result.entryCount());
        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            assertEquals("""
                    build.artifact=demo
                    build.group=com.example
                    build.name=demo
                    build.time=1970-01-01T00:00:00Z
                    build.version=0.1.0
                    """, readEntry(jar, "META-INF/build-info.properties"));
        }
    }

    @Test
    void packagesRequestedLibraryArtifacts() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Calculator.java", """
                package com.example;

                /** Adds numbers. */
                public final class Calculator {
                    /** Adds two integers. */
                    public int add(int left, int right) {
                        return left + right;
                    }
                }
                """);
        source(projectDir, "src/test/java/com/example/CalculatorTest.java", """
                package com.example;

                public final class CalculatorTest {
                }
                """);
        source(projectDir, "src/main/openapi/api.yaml", "openapi: 3.1.0\n");
        source(projectDir, "target/generated/sources/openapi/com/example/generated/GeneratedApi.java", """
                package com.example.generated;

                public interface GeneratedApi {
                }
                """);
        Files.createDirectories(projectDir.resolve("target/test-classes/com/example"));
        Files.write(projectDir.resolve("target/test-classes/com/example/CalculatorTest.class"), new byte[] {0});
        BuildSettings build = BuildSettings.defaults()
                .withGeneratedSources(
                        List.of(new GeneratedSourceStep(
                                "openapi",
                                GeneratedSourceKind.DECLARED_ROOT,
                                "java",
                                "target/generated/sources/openapi",
                                List.of("src/main/openapi/api.yaml"),
                                true,
                                false)),
                        List.of());
        ProjectConfig config = config(Optional.empty())
                .withBuildSettings(build)
                .withPackageSettings(new PackageSettings(PackageMode.THIN, true, true, true, null));

        PackageResult result = packageService.packageJar(projectDir, config, projectDir.resolve("cache"));

        assertEquals(List.of("sources", "javadoc", "tests"), result.artifacts().stream()
                .map(PackageArtifact::classifier)
                .toList());
        try (JarFile sources = new JarFile(projectDir.resolve("target/demo-0.1.0-sources.jar").toFile())) {
            assertNotNull(sources.getEntry("com/example/Calculator.java"));
            assertNull(sources.getEntry("com/example/generated/GeneratedApi.java"));
        }
        try (JarFile javadocs = new JarFile(projectDir.resolve("target/demo-0.1.0-javadoc.jar").toFile())) {
            assertTrue(javadocs.stream().anyMatch(entry -> entry.getName().endsWith("Calculator.html")));
        }
        try (JarFile tests = new JarFile(projectDir.resolve("target/demo-0.1.0-tests.jar").toFile())) {
            assertNotNull(tests.getEntry("com/example/CalculatorTest.class"));
        }
    }

    @Test
    void packagesJavadocsWhenProjectDirectoryIsRelative() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Calculator.java", """
                package com.example;

                /** Adds numbers. */
                public final class Calculator {
                    /** Adds two integers. */
                    public int add(int left, int right) {
                        return left + right;
                    }
                }
                """);
        Path classFile = projectDir.resolve("target/classes/com/example/Calculator.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, new byte[] {0});
        ProjectConfig config = config(Optional.empty())
                .withPackageSettings(new PackageSettings(PackageMode.THIN, false, true, false, null));
        Path relativeProjectDir = Path.of("")
                .toAbsolutePath()
                .normalize()
                .relativize(projectDir.toAbsolutePath().normalize());
        BuildResult buildResult = new BuildResult(
                Optional.empty(),
                1,
                0,
                relativeProjectDir.resolve("target/classes"),
                "",
                false);

        PackageResult result = packageService.packageJar(relativeProjectDir, config, buildResult, projectDir.resolve("cache"));

        assertEquals(projectDir.resolve("target/demo-0.1.0.jar"), result.jarPath());
        assertEquals(List.of("javadoc"), result.artifacts().stream()
                .map(PackageArtifact::classifier)
                .toList());
        try (JarFile javadocs = new JarFile(projectDir.resolve("target/demo-0.1.0-javadoc.jar").toFile())) {
            assertTrue(javadocs.stream().anyMatch(entry -> entry.getName().endsWith("Calculator.html")));
        }
    }

    @Test
    void javadocFailuresPreserveToolOutputAndNextStep() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Calculator.java", """
                package com.example;

                public final class Calculator {
                    /**
                     * Adds numbers.
                     * @param missing this parameter does not exist
                     */
                    public int add() {
                        return 1;
                    }
                }
                """);
        ProjectConfig config = config(Optional.empty())
                .withPackageSettings(new PackageSettings(PackageMode.THIN, false, true, false, null));

        PackageException exception = assertThrows(
                PackageException.class,
                () -> packageService.packageJar(projectDir, config, projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("javadoc failed with exit code"));
        assertTrue(exception.getMessage().contains("disable [package].javadoc"));
        assertTrue(exception.getMessage().contains("missing"));
    }

    @Test
    void duplicateJarEntriesFailWithActionableError() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        source(projectDir, "src/main/resources/META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n");

        PackageException exception = assertThrows(
                PackageException.class,
                () -> packageService.packageJar(
                        projectDir,
                        config(Optional.of("com.example.Main")),
                        projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("Duplicate jar entry `META-INF/MANIFEST.MF`"));
        assertTrue(exception.getMessage().contains("Remove or rename the duplicate resource"));
    }

    @Test
    void packagesLibraryProjectsWithoutMainClassManifestEntry() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Library.java", """
                package com.example;

                public final class Library {
                }
                """);

        PackageResult result = packageService.packageJar(
                projectDir,
                config(Optional.empty()),
                projectDir.resolve("cache"));

        assertFalse(result.hasMainClass());
        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            Attributes attributes = jar.getManifest().getMainAttributes();
            assertEquals("1.0", attributes.getValue(Attributes.Name.MANIFEST_VERSION));
            assertFalse(attributes.containsKey(Attributes.Name.MAIN_CLASS));
        }
    }

    @Test
    void doesNotBundleDependencyOrProcessorJarContents() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path dependencyJar = cacheRoot.resolve("com/example/lib/1.0.0/lib-1.0.0.jar");
        Path providedJar = cacheRoot.resolve("com/example/provided-api/1.0.0/provided-api-1.0.0.jar");
        Path devJar = cacheRoot.resolve("com/example/devtools/1.0.0/devtools-1.0.0.jar");
        Path processorJar = cacheRoot.resolve("com/example/processor/1.0.0/processor-1.0.0.jar");
        createJarWithEntry(dependencyJar, "com/example/lib/Lib.class");
        createJarWithEntry(providedJar, "com/example/provided/ProvidedApi.class");
        createJarWithEntry(devJar, "com/example/dev/DevTools.class");
        createJarWithEntry(processorJar, "com/example/processor/Processor.class");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "com/example/lib/1.0.0/lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:provided-api"
                version = "1.0.0"
                source = "maven-central"
                scope = "provided"
                direct = true
                jar = "com/example/provided-api/1.0.0/provided-api-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:devtools"
                version = "1.0.0"
                source = "maven-central"
                scope = "dev"
                direct = true
                jar = "com/example/devtools/1.0.0/devtools-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:processor"
                version = "1.0.0"
                source = "maven-central"
                scope = "processor"
                direct = true
                jar = "com/example/processor/1.0.0/processor-1.0.0.jar"
                dependencies = []
                """);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);

        PackageResult result = packageService.packageJar(projectDir, config(Optional.of("com.example.Main")), cacheRoot);

        Path runtimeClasspathPath = projectDir.resolve("target/demo-0.1.0.runtime-classpath");
        assertEquals(Optional.of(runtimeClasspathPath), result.runtimeClasspathPath());
        String runtimeClasspath = Files.readString(runtimeClasspathPath);
        assertTrue(runtimeClasspath.contains(dependencyJar.toString()));
        assertFalse(runtimeClasspath.contains(providedJar.toString()));
        assertFalse(runtimeClasspath.contains(devJar.toString()));
        assertFalse(runtimeClasspath.contains(processorJar.toString()));

        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            assertNotNull(jar.getEntry("com/example/Main.class"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals("com/example/lib/Lib.class")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals("com/example/provided/ProvidedApi.class")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals("com/example/processor/Processor.class")));
        }
    }

    @Test
    void writesRuntimeClasspathFromPrecomputedBuildClasspathPackages() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path runtimeJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Path devJar = cacheRoot.resolve("com/example/devtools/1.0.0/devtools-1.0.0.jar");
        createJarWithEntry(runtimeJar, "com/example/runtime/RuntimeLib.class");
        createJarWithEntry(devJar, "com/example/dev/DevTools.class");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:devtools"
                version = "1.0.0"
                source = "maven-central"
                scope = "dev"
                direct = true
                jar = "com/example/devtools/1.0.0/devtools-1.0.0.jar"
                dependencies = []
                """);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        ProjectConfig config = config(Optional.of("com.example.Main"));
        BuildResultWithClasspaths buildResult = new BuildService()
                .buildWithClasspaths(projectDir, config, cacheRoot, false);

        PackageResult result = packageService.packageJar(projectDir, config, buildResult, cacheRoot);

        Path runtimeClasspathPath = projectDir.resolve("target/demo-0.1.0.runtime-classpath");
        assertEquals(Optional.of(runtimeClasspathPath), result.runtimeClasspathPath());
        String runtimeClasspath = Files.readString(runtimeClasspathPath);
        assertTrue(buildResult.classpaths().runtime().entries().contains(devJar));
        assertTrue(runtimeClasspath.contains(runtimeJar.toString()));
        assertFalse(runtimeClasspath.contains(devJar.toString()));
    }

}
