package com.zolt.build.springboot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.BuildResult;
import com.zolt.build.PackageException;
import com.zolt.build.PackageResult;
import com.zolt.build.packaging.PackageRuntimeJar;
import com.zolt.dependency.PackageId;
import com.zolt.project.PackageMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SpringBootJarLayoutAssemblerTest {
    private final SpringBootJarLayoutAssembler assembler = new SpringBootJarLayoutAssembler();

    @TempDir
    private Path projectDir;

    @Test
    void assemblesExecutableJarLayoutWithLoaderClassesAndStoredRuntimeJars() throws IOException {
        Path outputDirectory = projectDir.resolve("target/classes");
        Path mainClass = outputDirectory.resolve("com/example/Main.class");
        Files.createDirectories(mainClass.getParent());
        Files.write(mainClass, new byte[] {1});
        Files.writeString(outputDirectory.resolve("application.properties"), "name=demo\n");
        Files.writeString(outputDirectory.resolve(".zolt-incremental-main.state"), "state\n");
        Path springBootJar = projectDir.resolve("cache/org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar");
        Path loaderJar = projectDir.resolve(
                "cache/org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar");
        Path runtimeJar = projectDir.resolve("cache/com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        createJarWithEntry(springBootJar, "org/springframework/boot/SpringApplication.class");
        createJarWithEntry(loaderJar, "org/springframework/boot/loader/launch/JarLauncher.class");
        createJarWithEntry(runtimeJar, "com/example/runtime/RuntimeLib.class");
        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");

        PackageResult result = assembler.assemble(
                "com.example.Main",
                new BuildResult(Optional.empty(), 1, 1, outputDirectory, ""),
                outputDirectory,
                jarPath,
                List.of(
                        new PackageRuntimeJar(SpringBootLoaderSupport.SPRING_BOOT_PACKAGE, "4.0.6", springBootJar),
                        new PackageRuntimeJar(SpringBootLoaderSupport.SPRING_BOOT_LOADER_PACKAGE, "4.0.6", loaderJar),
                        new PackageRuntimeJar(new PackageId("com.example", "runtime-lib"), "1.0.0", runtimeJar)));

        assertEquals(PackageMode.SPRING_BOOT, result.mode());
        assertEquals(jarPath, result.jarPath());
        assertEquals(Optional.empty(), result.runtimeClasspathPath());
        assertEquals(2, result.entryCount());
        assertTrue(result.hasMainClass());
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Attributes attributes = jar.getManifest().getMainAttributes();
            assertEquals("1.0", attributes.getValue(Attributes.Name.MANIFEST_VERSION));
            assertEquals(
                    "org.springframework.boot.loader.launch.JarLauncher",
                    attributes.getValue(Attributes.Name.MAIN_CLASS));
            assertEquals("com.example.Main", attributes.getValue("Start-Class"));
            assertEquals("4.0.6", attributes.getValue("Spring-Boot-Version"));
            assertEquals("BOOT-INF/classes/", attributes.getValue("Spring-Boot-Classes"));
            assertEquals("BOOT-INF/lib/", attributes.getValue("Spring-Boot-Lib"));
            assertNotNull(jar.getEntry("BOOT-INF/"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/"));
            assertNotNull(jar.getEntry("BOOT-INF/lib/"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/com/example/Main.class"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/application.properties"));
            assertNotNull(jar.getEntry("org/springframework/boot/loader/launch/JarLauncher.class"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(".zolt-incremental-main.state")));
            assertNotNull(jar.getEntry("BOOT-INF/lib/spring-boot-4.0.6.jar"));
            JarEntry runtimeEntry = jar.getJarEntry("BOOT-INF/lib/runtime-lib-1.0.0.jar");
            assertNotNull(runtimeEntry);
            assertEquals(JarEntry.STORED, runtimeEntry.getMethod());
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(
                    "BOOT-INF/lib/spring-boot-loader-4.0.6.jar")));
        }
    }

    @Test
    void reportsLoaderJarWithoutJarLauncher() throws IOException {
        Path outputDirectory = projectDir.resolve("target/classes");
        Files.createDirectories(outputDirectory);
        Path loaderJar = projectDir.resolve(
                "cache/org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar");
        createJarWithEntry(loaderJar, "org/springframework/boot/loader/Other.class");

        PackageException exception = assertThrows(
                PackageException.class,
                () -> assembler.assemble(
                        "com.example.Main",
                        new BuildResult(Optional.empty(), 0, 0, outputDirectory, ""),
                        outputDirectory,
                        projectDir.resolve("target/demo-0.1.0.jar"),
                        List.of(new PackageRuntimeJar(
                                SpringBootLoaderSupport.SPRING_BOOT_LOADER_PACKAGE,
                                "4.0.6",
                                loaderJar))));

        assertEquals(
                "Spring Boot loader classes were found, but JarLauncher is missing. Expected "
                        + "org.springframework.boot.loader.launch.JarLauncher or "
                        + "org.springframework.boot.loader.JarLauncher.",
                exception.getMessage());
    }

    private static void createJarWithEntry(Path jarPath, String entryName) throws IOException {
        Files.createDirectories(jarPath.getParent());
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            JarEntry entry = new JarEntry(entryName);
            jar.putNextEntry(entry);
            jar.write(new byte[] {1});
            jar.closeEntry();
        }
    }
}
