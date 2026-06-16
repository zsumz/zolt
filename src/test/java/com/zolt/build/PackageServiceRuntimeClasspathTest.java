package com.zolt.build;

import static com.zolt.build.PackageServiceTestSupport.config;
import static com.zolt.build.PackageServiceTestSupport.createJarWithEntry;
import static com.zolt.build.PackageServiceTestSupport.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageServiceRuntimeClasspathTest {
    private final PackageService packageService = new PackageService();

    @TempDir
    private Path projectDir;

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
