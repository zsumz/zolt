package com.zolt.build;

import static com.zolt.build.PackageServiceTestSupport.buildSettingsWithMetadata;
import static com.zolt.build.PackageServiceTestSupport.config;
import static com.zolt.build.PackageServiceTestSupport.createJarWithEntry;
import static com.zolt.build.PackageServiceTestSupport.readEntry;
import static com.zolt.build.PackageServiceTestSupport.resourceFilteringSettings;
import static com.zolt.build.PackageServiceTestSupport.source;
import static com.zolt.build.PackageServiceTestSupport.writeLockfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.framework.FrameworkPackageResult;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.BuildMetadataSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import com.zolt.resolve.ResolveService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageServiceFrameworkModesTest {
    private final PackageService packageService = new PackageService();

    @TempDir
    private Path projectDir;

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
        source(projectDir, "src/main/java/com/example/Main.java", """
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
    void packagesWarLayoutWithRuntimeDependenciesOnly() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path runtimeJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Path providedJar = cacheRoot.resolve("jakarta/servlet/jakarta.servlet-api/6.1.0/jakarta.servlet-api-6.1.0.jar");
        Path devJar = cacheRoot.resolve("com/example/devtools/1.0.0/devtools-1.0.0.jar");
        createJarWithEntry(runtimeJar, "com/example/runtime/RuntimeLib.class");
        createJarWithEntry(providedJar, "jakarta/servlet/Servlet.class");
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
                id = "jakarta.servlet:jakarta.servlet-api"
                version = "6.1.0"
                source = "maven-central"
                scope = "provided"
                direct = true
                jar = "jakarta/servlet/jakarta.servlet-api/6.1.0/jakarta.servlet-api-6.1.0.jar"
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
        source(projectDir, "src/main/resources/application.properties", "name=@projectName@\n");
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withPackageSettings(new PackageSettings(PackageMode.WAR));
        config = config.withBuildSettings(config.build().withResourceFiltering(resourceFilteringSettings()));

        PackageResult result = packageService.packageJar(projectDir, config, cacheRoot);

        assertEquals(PackageMode.WAR, result.mode());
        assertEquals(projectDir.resolve("target/demo-0.1.0.war"), result.jarPath());
        assertEquals(Optional.empty(), result.runtimeClasspathPath());
        assertFalse(result.hasMainClass());
        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            assertNotNull(jar.getEntry("META-INF/MANIFEST.MF"));
            assertFalse(jar.getManifest().getMainAttributes().containsKey(Attributes.Name.MAIN_CLASS));
            assertNotNull(jar.getEntry("WEB-INF/"));
            assertNotNull(jar.getEntry("WEB-INF/classes/"));
            assertNotNull(jar.getEntry("WEB-INF/lib/"));
            assertNotNull(jar.getEntry("WEB-INF/classes/com/example/Main.class"));
            assertEquals("name=demo\n", readEntry(jar, "WEB-INF/classes/application.properties"));
            assertNotNull(jar.getEntry("WEB-INF/lib/runtime-lib-1.0.0.jar"));
            assertEquals(JarEntry.STORED, jar.getEntry("WEB-INF/lib/runtime-lib-1.0.0.jar").getMethod());
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(
                    "WEB-INF/lib/jakarta.servlet-api-6.1.0.jar")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(
                    "WEB-INF/lib/devtools-1.0.0.jar")));
        }
    }

    @Test
    void packagesSpringBootWarLayoutWithProvidedDependencies() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path springBootJar = cacheRoot.resolve("org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar");
        Path loaderJar = cacheRoot.resolve(
                "org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar");
        Path runtimeJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Path providedJar = cacheRoot.resolve("org/apache/tomcat/embed/tomcat-embed-core/10.1.40/tomcat-embed-core-10.1.40.jar");
        Path devJar = cacheRoot.resolve("com/example/devtools/1.0.0/devtools-1.0.0.jar");
        createJarWithEntry(springBootJar, "org/springframework/boot/SpringApplication.class");
        createJarWithEntry(loaderJar, "org/springframework/boot/loader/launch/WarLauncher.class");
        createJarWithEntry(runtimeJar, "com/example/runtime/RuntimeLib.class");
        createJarWithEntry(providedJar, "org/apache/catalina/startup/Tomcat.class");
        createJarWithEntry(devJar, "com/example/dev/DevTools.class");
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
                id = "org.apache.tomcat.embed:tomcat-embed-core"
                version = "10.1.40"
                source = "maven-central"
                scope = "provided"
                direct = true
                jar = "org/apache/tomcat/embed/tomcat-embed-core/10.1.40/tomcat-embed-core-10.1.40.jar"
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

        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT_WAR));

        PackageResult result = packageService.packageJar(projectDir, config, cacheRoot);

        assertEquals(PackageMode.SPRING_BOOT_WAR, result.mode());
        assertEquals(projectDir.resolve("target/demo-0.1.0.war"), result.jarPath());
        assertTrue(result.hasMainClass());
        String evidence = Files.readString(result.evidenceManifestPath().orElseThrow());
        assertTrue(evidence.contains("\"archive\": \"target/demo-0.1.0.war\""));
        assertTrue(evidence.contains("\"coordinate\": \"org.apache.tomcat.embed:tomcat-embed-core:10.1.40\""));
        assertTrue(evidence.contains("\"scope\": \"provided\""));
        assertTrue(evidence.contains("\"disposition\": \"provided\""));
        assertTrue(evidence.contains("\"rule\": \"spring-boot-war-provided-lib\""));
        assertTrue(evidence.contains("\"location\": \"WEB-INF/lib-provided/tomcat-embed-core-10.1.40.jar\""));
        assertTrue(evidence.contains("\"laneDisposition\": \"provided-container\""));
        assertTrue(evidence.contains("\"coordinate\": \"com.example:devtools:1.0.0\""));
        assertTrue(evidence.contains("\"rule\": \"dev-only-omitted\""));
        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            Attributes attributes = jar.getManifest().getMainAttributes();
            assertEquals(
                    "org.springframework.boot.loader.launch.WarLauncher",
                    attributes.getValue(Attributes.Name.MAIN_CLASS));
            assertEquals("com.example.Main", attributes.getValue("Start-Class"));
            assertEquals("4.0.6", attributes.getValue("Spring-Boot-Version"));
            assertEquals("WEB-INF/classes/", attributes.getValue("Spring-Boot-Classes"));
            assertEquals("WEB-INF/lib/", attributes.getValue("Spring-Boot-Lib"));
            assertEquals("WEB-INF/lib-provided/", attributes.getValue("Spring-Boot-Lib-Provided"));
            assertNotNull(jar.getEntry("org/springframework/boot/loader/launch/WarLauncher.class"));
            assertNotNull(jar.getEntry("WEB-INF/classes/com/example/Main.class"));
            assertNotNull(jar.getEntry("WEB-INF/lib/runtime-lib-1.0.0.jar"));
            assertNotNull(jar.getEntry("WEB-INF/lib/spring-boot-4.0.6.jar"));
            assertNotNull(jar.getEntry("WEB-INF/lib-provided/tomcat-embed-core-10.1.40.jar"));
            assertEquals(1, jar.stream()
                    .filter(entry -> entry.getName().equals("WEB-INF/lib/runtime-lib-1.0.0.jar"))
                    .count());
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(
                    "WEB-INF/lib/spring-boot-loader-4.0.6.jar")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(
                    "WEB-INF/lib/devtools-1.0.0.jar")));
        }
    }

    @Test
    void unsupportedUberPackageModeFailsBeforeWritingJar() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", """
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
        assertTrue(exception.getMessage().contains(
                "Supported package modes are: thin, spring-boot, war, spring-boot-war, quarkus"));
        assertTrue(exception.getMessage().contains("Intentionally unsupported package modes are: uber"));
        assertFalse(Files.exists(projectDir.resolve("target/demo-0.1.0.jar")));
    }

    @Test
    void quarkusPackageModeReturnsAugmentedRunnerJar() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        Path packageDirectory = projectDir.resolve("target/quarkus-app");
        Path runnerJar = packageDirectory.resolve("quarkus-run.jar");
        createJarWithEntry(runnerJar, "io/quarkus/bootstrap/runner/QuarkusEntryPoint.class");
        Files.createDirectories(packageDirectory.resolve("app"));
        Files.writeString(packageDirectory.resolve("app/app.jar"), "app\n");
        boolean[] augmented = new boolean[1];
        PackageService service = new PackageService(
                new BuildService(),
                new ResolveService(),
                new ManifestGenerator(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                (projectDirectory, config, cacheRoot) -> {
                    augmented[0] = true;
                    return Optional.of(new FrameworkPackageResult(
                            PackageMode.QUARKUS,
                            packageDirectory,
                            runnerJar,
                            "target/quarkus-app/app"));
                });
        ProjectConfig config = config(Optional.empty())
                .withPackageSettings(new PackageSettings(PackageMode.QUARKUS))
                .withFrameworkSettings(new FrameworkSettings(new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));

        PackageResult result = service.packageJar(projectDir, config, projectDir.resolve("cache"));

        assertEquals(PackageMode.QUARKUS, result.mode());
        assertEquals(runnerJar, result.jarPath());
        assertEquals("target/quarkus-app/app", result.applicationLayout());
        assertEquals(2, result.entryCount());
        assertTrue(result.hasMainClass());
        assertTrue(augmented[0]);
    }

    @Test
    void quarkusPackageModeRequiresEnabledFramework() throws IOException {
        writeLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        PackageService service = new PackageService(
                new BuildService(),
                new ResolveService(),
                new ManifestGenerator(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                (projectDirectory, config, cacheRoot) -> Optional.empty());
        ProjectConfig config = config(Optional.empty())
                .withPackageSettings(new PackageSettings(PackageMode.QUARKUS));

        PackageException exception = assertThrows(
                PackageException.class,
                () -> service.packageJar(projectDir, config, projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("Framework package mode `quarkus` requires a matching framework adapter"));
        assertTrue(exception.getMessage().contains("Enable the framework in zolt.toml"));
    }

    @Test
    void packagesSpringBootExecutableJarLayout() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path springBootJar = cacheRoot.resolve("org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar");
        Path loaderJar = cacheRoot.resolve(
                "org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar");
        Path dependencyJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Path devJar = cacheRoot.resolve("com/example/devtools/1.0.0/devtools-1.0.0.jar");
        Path processorJar = cacheRoot.resolve("com/example/processor/1.0.0/processor-1.0.0.jar");
        createJarWithEntry(springBootJar, "org/springframework/boot/SpringApplication.class");
        createJarWithEntry(loaderJar, "org/springframework/boot/loader/launch/JarLauncher.class");
        createJarWithEntry(dependencyJar, "com/example/runtime/RuntimeLib.class");
        createJarWithEntry(devJar, "com/example/dev/DevTools.class");
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
        source(projectDir, "src/main/resources/application.properties", "server.port=@serverPort@\nname=@projectName@\n");
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withBuildSettings(buildSettingsWithMetadata(new BuildMetadataSettings(true, false, true)))
                .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT));
        config = config.withBuildSettings(config.build().withResourceFiltering(resourceFilteringSettings()));

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
            assertEquals("server.port=0\nname=demo\n", readEntry(jar, "BOOT-INF/classes/application.properties"));
            assertEquals("""
                    build.artifact=demo
                    build.group=com.example
                    build.name=demo
                    build.time=1970-01-01T00:00:00Z
                    build.version=0.1.0
                    """, readEntry(jar, "BOOT-INF/classes/META-INF/build-info.properties"));
            assertNotNull(jar.getEntry("BOOT-INF/lib/runtime-lib-1.0.0.jar"));
            assertNotNull(jar.getEntry("BOOT-INF/lib/spring-boot-4.0.6.jar"));
            assertEquals(1, jar.stream()
                    .filter(entry -> entry.getName().equals("BOOT-INF/lib/runtime-lib-1.0.0.jar"))
                    .count());
            assertEquals(JarEntry.STORED, jar.getEntry("BOOT-INF/lib/runtime-lib-1.0.0.jar").getMethod());
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(
                    "BOOT-INF/lib/spring-boot-loader-4.0.6.jar")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(
                    "BOOT-INF/lib/devtools-1.0.0.jar")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(
                    "BOOT-INF/lib/processor-1.0.0.jar")));
        }
    }

    @Test
    void springBootPackageUsesPrecomputedBuildClasspathPackages() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path springBootJar = cacheRoot.resolve("org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar");
        Path loaderJar = cacheRoot.resolve(
                "org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar");
        Path dependencyJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        createJarWithEntry(springBootJar, "org/springframework/boot/SpringApplication.class");
        createJarWithEntry(loaderJar, "org/springframework/boot/loader/launch/JarLauncher.class");
        createJarWithEntry(dependencyJar, "com/example/runtime/RuntimeLib.class");
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
        source(projectDir, "src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        ProjectConfig config = config(Optional.of("com.example.Main"))
                .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT));
        BuildResultWithClasspaths buildResult = new BuildService()
                .buildWithClasspaths(projectDir, config, cacheRoot, false);
        Files.delete(projectDir.resolve("zolt.lock"));

        PackageResult result = packageService.packageJar(projectDir, config, buildResult, cacheRoot);

        assertEquals(PackageMode.SPRING_BOOT, result.mode());
        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            assertNotNull(jar.getEntry("BOOT-INF/classes/com/example/Main.class"));
            assertNotNull(jar.getEntry("BOOT-INF/lib/runtime-lib-1.0.0.jar"));
            assertNotNull(jar.getEntry("org/springframework/boot/loader/launch/JarLauncher.class"));
        }
    }

}
