package com.zolt.build;

import static com.zolt.build.PackageServiceTestSupport.config;
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
import com.zolt.project.BuildSettings;
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

}
