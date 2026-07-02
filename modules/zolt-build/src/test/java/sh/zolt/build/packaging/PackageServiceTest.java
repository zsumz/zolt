package sh.zolt.build.packaging;

import static sh.zolt.build.packaging.PackageServiceTestSupport.config;
import static sh.zolt.build.packaging.PackageServiceTestSupport.readEntry;
import static sh.zolt.build.packaging.PackageServiceTestSupport.resourceFilteringSettings;
import static sh.zolt.build.packaging.PackageServiceTestSupport.source;
import static sh.zolt.build.packaging.PackageServiceTestSupport.writeLockfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.BuildSettings;
import sh.zolt.project.PackageMode;
import sh.zolt.project.PackageSettings;
import sh.zolt.project.ProjectConfig;
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
    void packagesArchiveAndRuntimeClasspathUnderOutputRoot() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withBuildSettings(new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        ".zolt/build",
                        ".zolt/build/classes",
                        ".zolt/build/test-classes"));

        PackageResult result = packageService.packageJar(projectDir, config, projectDir.resolve("cache"));

        assertEquals(projectDir.resolve(".zolt/build/demo-0.1.0.jar"), result.jarPath());
        assertEquals(Optional.of(projectDir.resolve(".zolt/build/demo-0.1.0.runtime-classpath")), result.runtimeClasspathPath());
        assertEquals(Optional.of(projectDir.resolve(".zolt/build/demo-0.1.0.jar.zolt-package.json")), result.evidenceManifestPath());
        assertTrue(Files.exists(projectDir.resolve(".zolt/build/demo-0.1.0.jar")));
        assertTrue(Files.exists(projectDir.resolve(".zolt/build/demo-0.1.0.runtime-classpath")));
        assertFalse(Files.exists(projectDir.resolve("target/demo-0.1.0.jar")));
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

}
