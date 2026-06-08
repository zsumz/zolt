package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageServiceTest {
    private final PackageService packageService = new PackageService();

    @TempDir
    private Path projectDir;

    @Test
    void packagesCompiledClassesWithGeneratedManifest() throws IOException {
        writeLockfile();
        source("src/main/java/com/example/Main.java", """
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
            Attributes attributes = jar.getManifest().getMainAttributes();
            assertEquals("1.0", attributes.getValue(Attributes.Name.MANIFEST_VERSION));
            assertEquals("com.example.Main", attributes.getValue(Attributes.Name.MAIN_CLASS));
        }
    }

    @Test
    void packagesResourcesAndNativeImageConfigDeterministically() throws IOException {
        writeLockfile();
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        source("src/main/resources/config/app.properties", "name=demo\n");
        source("src/main/resources/META-INF/native-image/reflect-config.json", "[]\n");

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
    void duplicateJarEntriesFailWithActionableError() throws IOException {
        writeLockfile();
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        source("src/main/resources/META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n");

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
    void springBootPackageModeRequiresResolvedLoaderArtifact() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        createJarWithEntry(
                cacheRoot.resolve("org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar"),
                "org/springframework/boot/SpringApplication.class");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.springframework.boot:spring-boot"
                version = "4.0.6"
                source = "maven-central"
                scope = "compile"
                direct = false
                jar = "org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar"
                dependencies = []
                """);
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT));

        PackageException exception = assertThrows(
                PackageException.class,
                () -> packageService.packageJar(projectDir, config, cacheRoot));

        assertTrue(exception.getMessage().contains("requires `org.springframework.boot:spring-boot-loader`"));
        assertTrue(exception.getMessage().contains("run `zolt resolve`"));
        assertTrue(exception.getMessage().contains("4.0.6"));
        assertFalse(Files.exists(projectDir.resolve("target/demo-0.1.0.jar")));
    }

    @Test
    void unsupportedUberPackageModeFailsBeforeWritingJar() throws IOException {
        writeLockfile();
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withPackageSettings(new PackageSettings(PackageMode.UBER));

        PackageException exception = assertThrows(
                PackageException.class,
                () -> packageService.packageJar(projectDir, config, projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("Package mode `uber` is not implemented yet"));
        assertTrue(exception.getMessage().contains("Use `zolt package --mode thin`"));
        assertFalse(Files.exists(projectDir.resolve("target/demo-0.1.0.jar")));
    }

    @Test
    void packagesSpringBootExecutableJarLayout() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path springBootJar = cacheRoot.resolve("org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar");
        Path loaderJar = cacheRoot.resolve(
                "org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar");
        Path dependencyJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Path processorJar = cacheRoot.resolve("com/example/processor/1.0.0/processor-1.0.0.jar");
        createJarWithEntry(springBootJar, "org/springframework/boot/SpringApplication.class");
        createJarWithEntry(loaderJar, "org/springframework/boot/loader/launch/JarLauncher.class");
        createJarWithEntry(dependencyJar, "com/example/runtime/RuntimeLib.class");
        createJarWithEntry(processorJar, "com/example/processor/Processor.class");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.springframework.boot:spring-boot"
                version = "4.0.6"
                source = "maven-central"
                scope = "compile"
                direct = false
                jar = "org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar"
                dependencies = []

                [[package]]
                id = "org.springframework.boot:spring-boot-loader"
                version = "4.0.6"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar"
                dependencies = []

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = false
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
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
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        source("src/main/resources/application.properties", "server.port=0\n");
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT));

        PackageResult result = packageService.packageJar(projectDir, config, cacheRoot);

        assertEquals(PackageMode.SPRING_BOOT, result.mode());
        assertEquals(Optional.empty(), result.runtimeClasspathPath());
        assertTrue(result.hasMainClass());
        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            Attributes attributes = jar.getManifest().getMainAttributes();
            assertEquals("1.0", attributes.getValue(Attributes.Name.MANIFEST_VERSION));
            assertEquals(
                    "org.springframework.boot.loader.launch.JarLauncher",
                    attributes.getValue(Attributes.Name.MAIN_CLASS));
            assertEquals("com.example.Main", attributes.getValue("Start-Class"));
            assertEquals("4.0.6", attributes.getValue("Spring-Boot-Version"));
            assertEquals("BOOT-INF/classes/", attributes.getValue("Spring-Boot-Classes"));
            assertEquals("BOOT-INF/lib/", attributes.getValue("Spring-Boot-Lib"));
            assertNotNull(jar.getEntry("org/springframework/boot/loader/launch/JarLauncher.class"));
            assertNotNull(jar.getEntry("BOOT-INF/"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/com/"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/com/example/"));
            assertNotNull(jar.getEntry("BOOT-INF/lib/"));
            assertNotNull(jar.getEntry("org/"));
            assertNotNull(jar.getEntry("org/springframework/"));
            assertNotNull(jar.getEntry("org/springframework/boot/loader/launch/"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/com/example/Main.class"));
            assertEquals("server.port=0\n", readEntry(jar, "BOOT-INF/classes/application.properties"));
            assertNotNull(jar.getEntry("BOOT-INF/lib/runtime-lib-1.0.0.jar"));
            assertNotNull(jar.getEntry("BOOT-INF/lib/spring-boot-4.0.6.jar"));
            assertEquals(1, jar.stream()
                    .filter(entry -> entry.getName().equals("BOOT-INF/lib/runtime-lib-1.0.0.jar"))
                    .count());
            assertEquals(JarEntry.STORED, jar.getEntry("BOOT-INF/lib/runtime-lib-1.0.0.jar").getMethod());
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(
                    "BOOT-INF/lib/spring-boot-loader-4.0.6.jar")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(
                    "BOOT-INF/lib/processor-1.0.0.jar")));
        }
    }

    @Test
    void packagesLibraryProjectsWithoutMainClassManifestEntry() throws IOException {
        writeLockfile();
        source("src/main/java/com/example/Library.java", """
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
        Path processorJar = cacheRoot.resolve("com/example/processor/1.0.0/processor-1.0.0.jar");
        createJarWithEntry(dependencyJar, "com/example/lib/Lib.class");
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
                id = "com.example:processor"
                version = "1.0.0"
                source = "maven-central"
                scope = "processor"
                direct = true
                jar = "com/example/processor/1.0.0/processor-1.0.0.jar"
                dependencies = []
                """);
        source("src/main/java/com/example/Main.java", """
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
        assertFalse(runtimeClasspath.contains(processorJar.toString()));

        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            assertNotNull(jar.getEntry("com/example/Main.class"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals("com/example/lib/Lib.class")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals("com/example/processor/Processor.class")));
        }
    }

    private static ProjectConfig config(Optional<String> mainClass) {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", currentJavaMajorVersion(), mainClass),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }

    private void source(String path, String content) throws IOException {
        Path source = projectDir.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }

    private void writeLockfile() throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
    }

    private static void createJarWithEntry(Path jarPath, String entryName) throws IOException {
        Files.createDirectories(jarPath.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            output.putNextEntry(new JarEntry(entryName));
            output.write(new byte[] {0});
            output.closeEntry();
        }
    }

    private static String readEntry(JarFile jar, String name) throws IOException {
        try (InputStream input = jar.getInputStream(jar.getEntry(name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }
}
