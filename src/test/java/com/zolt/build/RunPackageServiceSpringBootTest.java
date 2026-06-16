package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.project.BuildSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RunPackageServiceSpringBootTest {
    @TempDir
    private Path projectDir;

    @Test
    void runsSpringBootPackageWithJavaJar() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        writeSpringBootLockfile(cacheRoot);
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        List<List<String>> commands = new ArrayList<>();
        RunPackageService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "boot\n");
        });

        RunPackageResult result = service.runPackage(
                projectDir,
                config(Optional.of("com.example.Main"))
                        .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT)),
                cacheRoot,
                List.of("one", "two"));

        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        assertEquals(PackageMode.SPRING_BOOT, result.packageResult().mode());
        assertEquals("boot\n", result.javaRunResult().output());
        assertEquals(List.of(
                commands.getFirst().get(0),
                "-jar",
                jarPath.toString(),
                "one",
                "two"), commands.getFirst());
    }

    @Test
    void runsSpringBootWarPackageWithJavaJar() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        writeSpringBootWarLockfile(cacheRoot);
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        List<List<String>> commands = new ArrayList<>();
        RunPackageService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "boot war\n");
        });

        RunPackageResult result = service.runPackage(
                projectDir,
                config(Optional.of("com.example.Main"))
                        .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT_WAR)),
                cacheRoot,
                List.of("one", "two"));

        Path warPath = projectDir.resolve("target/demo-0.1.0.war");
        assertEquals(PackageMode.SPRING_BOOT_WAR, result.packageResult().mode());
        assertEquals("boot war\n", result.javaRunResult().output());
        assertEquals(List.of(
                commands.getFirst().get(0),
                "-jar",
                warPath.toString(),
                "one",
                "two"), commands.getFirst());
    }

    private RunPackageService service(JavaRunner.ProcessRunner processRunner) {
        return service(processRunner, new JdkDetector());
    }

    private RunPackageService service(JavaRunner.ProcessRunner processRunner, JdkChecker jdkChecker) {
        return new RunPackageService(
                new PackageService(),
                new BuildService(jdkChecker),
                new ClasspathBuilder(),
                jdkChecker,
                new JavaRunner(":", processRunner));
    }

    private void writeSpringBootLockfile(Path cacheRoot) throws IOException {
        createJarWithEntry(
                cacheRoot.resolve("org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar"),
                "org/springframework/boot/SpringApplication.class");
        createJarWithEntry(
                cacheRoot.resolve("org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar"),
                "org/springframework/boot/loader/launch/JarLauncher.class");
        createJarWithEntry(
                cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"),
                "com/example/runtime/RuntimeLib.class");
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
                """);
    }

    private void writeSpringBootWarLockfile(Path cacheRoot) throws IOException {
        createJarWithEntry(
                cacheRoot.resolve("org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar"),
                "org/springframework/boot/SpringApplication.class");
        createJarWithEntry(
                cacheRoot.resolve("org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar"),
                "org/springframework/boot/loader/launch/WarLauncher.class");
        createJarWithEntry(
                cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"),
                "com/example/runtime/RuntimeLib.class");
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
                """);
    }

    private void source(String path, String content) throws IOException {
        Path source = projectDir.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }

    private static void createJarWithEntry(Path jarPath, String entryName) throws IOException {
        Files.createDirectories(jarPath.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            output.putNextEntry(new JarEntry(entryName));
            output.write(new byte[] {0});
            output.closeEntry();
        }
    }

    private static ProjectConfig config(Optional<String> mainClass) {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata("demo", "0.1.0", "com.example", currentJavaMajorVersion(), mainClass),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
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
