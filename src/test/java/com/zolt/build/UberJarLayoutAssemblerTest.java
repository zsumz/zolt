package com.zolt.build;

import static com.zolt.build.PackageServiceTestSupport.createJarWithEntries;
import static com.zolt.build.PackageServiceTestSupport.readEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.classpath.ResolvedPackage;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class UberJarLayoutAssemblerTest {
    private final UberJarLayoutAssembler assembler = new UberJarLayoutAssembler(new ManifestGenerator());

    @TempDir
    private Path projectDir;

    @Test
    void mergesApplicationOutputAndRuntimeDependencyJarsDeterministically() throws IOException {
        Path outputDirectory = projectDir.resolve("target/classes");
        PackageServiceTestSupport.source(outputDirectory, "com/example/Main.class", "main");
        PackageServiceTestSupport.source(outputDirectory, "config/app.properties", "app=true\n");
        PackageServiceTestSupport.source(outputDirectory, ".zolt-build-main.fingerprint", "local");
        Path runtimeJar = projectDir.resolve("cache/com/example/runtime/1.0.0/runtime-1.0.0.jar");
        createJarWithEntries(
                runtimeJar,
                Map.of(
                        "META-INF/MANIFEST.MF", "ignored",
                        "META-INF/INDEX.LIST", "ignored",
                        "META-INF/DEMO.SF", "ignored",
                        "MODULE-INFO.CLASS", "ignored",
                        "com/example/runtime/Runtime.class", "runtime",
                        "runtime.properties", "merged=true\n"));

        PackageResult result = assembler.assemble(
                PackageServiceTestSupport.config(Optional.of("com.example.Main"))
                        .withPackageSettings(new PackageSettings(PackageMode.UBER)),
                new BuildResult(Optional.empty(), 1, 1, outputDirectory, ""),
                outputDirectory,
                projectDir.resolve("target/demo-0.1.0.jar"),
                List.of(runtimeDependency("com.example", "runtime", "1.0.0", runtimeJar)));

        assertEquals(PackageMode.UBER, result.mode());
        assertEquals("archive root", result.applicationLayout());
        assertTrue(result.hasMainClass());
        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            assertEquals(List.of(
                    "META-INF/",
                    "META-INF/MANIFEST.MF",
                    "com/",
                    "com/example/",
                    "com/example/Main.class",
                    "config/",
                    "config/app.properties",
                    "com/example/runtime/",
                    "com/example/runtime/Runtime.class",
                    "runtime.properties"), jar.stream().map(java.util.jar.JarEntry::getName).toList());
            assertEquals("main", readEntry(jar, "com/example/Main.class"));
            assertEquals("runtime", readEntry(jar, "com/example/runtime/Runtime.class"));
            assertEquals("merged=true\n", readEntry(jar, "runtime.properties"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(".zolt-build-main.fingerprint")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals("META-INF/DEMO.SF")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals("MODULE-INFO.CLASS")));
            Attributes attributes = jar.getManifest().getMainAttributes();
            assertEquals("com.example.Main", attributes.getValue(Attributes.Name.MAIN_CLASS));
        }
    }

    @Test
    void failsWhenRuntimeJarsProduceDuplicateEntries() throws IOException {
        Path outputDirectory = projectDir.resolve("target/classes");
        Files.createDirectories(outputDirectory);
        Path first = projectDir.resolve("cache/com/example/first/1.0.0/first-1.0.0.jar");
        Path second = projectDir.resolve("cache/com/example/second/1.0.0/second-1.0.0.jar");
        createJarWithEntries(first, Map.of("shared.txt", "one"));
        createJarWithEntries(second, Map.of("shared.txt", "two"));

        PackageException exception = assertThrows(PackageException.class, () -> assembler.assemble(
                PackageServiceTestSupport.config(Optional.empty())
                        .withPackageSettings(new PackageSettings(PackageMode.UBER)),
                new BuildResult(Optional.empty(), 0, 0, outputDirectory, ""),
                outputDirectory,
                projectDir.resolve("target/demo-0.1.0.jar"),
                List.of(
                        runtimeDependency("com.example", "first", "1.0.0", first),
                        runtimeDependency("com.example", "second", "1.0.0", second))));

        assertTrue(exception.getMessage().contains("Duplicate uber jar entry `shared.txt`"));
        assertTrue(exception.getMessage().contains("Move one dependency out of the runtime classpath"));
        assertTrue(exception.getMessage().contains("thin"));
    }

    private static PackageRuntimeJar runtimeDependency(String group, String artifact, String version, Path jar) {
        ResolvedClasspathPackage dependency = new ResolvedClasspathPackage(
                new ResolvedPackage(
                        new PackageId(group, artifact),
                        version,
                        false,
                        Path.of("pom.xml"),
                        jar),
                DependencyScope.RUNTIME);
        return new PackageRuntimeJar(
                dependency.resolvedPackage().packageId(),
                dependency.resolvedPackage().selectedVersion(),
                dependency.resolvedPackage().jarPath());
    }
}
