package com.zolt.build.packaging;

import static com.zolt.build.packaging.PackageServiceTestSupport.config;
import static com.zolt.build.packaging.PackageServiceTestSupport.createJarWithEntry;
import static com.zolt.build.packaging.PackageServiceTestSupport.source;
import static com.zolt.build.packaging.PackageServiceTestSupport.writeLockfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.build.BuildService;
import com.zolt.build.PackageException;
import com.zolt.classpath.ClasspathBuilder;
import com.zolt.build.manifest.ManifestGenerator;
import com.zolt.framework.FrameworkPackageResult;
import com.zolt.lockfile.ZoltLockfileReader;
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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageServiceQuarkusModeTest {
    @TempDir
    private Path projectDir;

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
}
