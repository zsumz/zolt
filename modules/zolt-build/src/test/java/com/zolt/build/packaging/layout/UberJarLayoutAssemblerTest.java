package com.zolt.build.packaging.layout;

import static com.zolt.build.packaging.PackageServiceTestSupport.createJarWithEntries;
import static com.zolt.build.packaging.PackageServiceTestSupport.readEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.BuildResult;
import com.zolt.build.manifest.ManifestGenerator;
import com.zolt.build.PackageException;
import com.zolt.build.packaging.PackageMergeDecision;
import com.zolt.build.packaging.PackageResult;
import com.zolt.build.packaging.PackageRuntimeJar;
import com.zolt.build.packaging.PackageServiceTestSupport;
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
                        "META-INF/versions/9/module-info.class", "ignored",
                        "META-INF/LICENSE.txt", "license",
                        "com/example/runtime/Runtime.class", "runtime",
                        "runtime.properties", "merged=true\n"));

        PackageResult result = assembler.assemble(
                projectDir,
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
                    "META-INF/zolt-uber/",
                    "META-INF/zolt-uber/com/",
                    "META-INF/zolt-uber/com/example/",
                    "META-INF/zolt-uber/com/example/runtime/",
                    "META-INF/zolt-uber/com/example/runtime/1.0.0/",
                    "META-INF/zolt-uber/com/example/runtime/1.0.0/LICENSE.txt",
                    "com/example/runtime/",
                    "com/example/runtime/Runtime.class",
                    "runtime.properties"), jar.stream().map(java.util.jar.JarEntry::getName).toList());
            assertEquals("main", readEntry(jar, "com/example/Main.class"));
            assertEquals("runtime", readEntry(jar, "com/example/runtime/Runtime.class"));
            assertEquals("merged=true\n", readEntry(jar, "runtime.properties"));
            assertEquals(
                    "license",
                    readEntry(jar, "META-INF/zolt-uber/com/example/runtime/1.0.0/LICENSE.txt"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(".zolt-build-main.fingerprint")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals("META-INF/DEMO.SF")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals("MODULE-INFO.CLASS")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals("META-INF/versions/9/module-info.class")));
            Attributes attributes = jar.getManifest().getMainAttributes();
            assertEquals("com.example.Main", attributes.getValue(Attributes.Name.MAIN_CLASS));
        }
        assertTrue(result.mergeDecisions().contains(new PackageMergeDecision(
                "relocated-metadata",
                "META-INF/LICENSE.txt",
                Optional.of("META-INF/zolt-uber/com/example/runtime/1.0.0/LICENSE.txt"),
                List.of("com.example:runtime"))));
        assertTrue(result.mergeDecisions().contains(new PackageMergeDecision(
                "omitted-module-descriptor",
                "MODULE-INFO.CLASS",
                Optional.empty(),
                List.of("com.example:runtime"))));
        assertTrue(result.mergeDecisions().contains(new PackageMergeDecision(
                "omitted-module-descriptor",
                "META-INF/versions/9/module-info.class",
                Optional.empty(),
                List.of("com.example:runtime"))));
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
                projectDir,
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

    @Test
    void mergesServiceDescriptorsAndNettyVersionMetadataDeterministically() throws IOException {
        Path outputDirectory = projectDir.resolve("target/classes");
        PackageServiceTestSupport.source(
                outputDirectory,
                "META-INF/services/com.example.Plugin",
                "com.example.AppPlugin\n");
        Path first = projectDir.resolve("cache/com/example/first/1.0.0/first-1.0.0.jar");
        Path second = projectDir.resolve("cache/com/example/second/1.0.0/second-1.0.0.jar");
        createJarWithEntries(first, Map.of(
                "META-INF/services/com.example.Plugin", "com.example.FirstPlugin\ncom.example.SharedPlugin\n",
                "META-INF/io.netty.versions.properties", """
                        # generated
                        netty-common.version=4.1.115.Final
                        netty-common.repoStatus=clean
                        """));
        createJarWithEntries(second, Map.of(
                "META-INF/services/com.example.Plugin", "com.example.SecondPlugin\ncom.example.SharedPlugin\n",
                "META-INF/io.netty.versions.properties", """
                        # generated
                        netty-buffer.version=4.1.115.Final
                        netty-buffer.repoStatus=clean
                        """));

        PackageResult result = assembler.assemble(
                projectDir,
                PackageServiceTestSupport.config(Optional.empty())
                        .withPackageSettings(new PackageSettings(PackageMode.UBER)),
                new BuildResult(Optional.empty(), 0, 0, outputDirectory, ""),
                outputDirectory,
                projectDir.resolve("target/demo-0.1.0.jar"),
                List.of(
                        runtimeDependency("com.example", "first", "1.0.0", first),
                        runtimeDependency("com.example", "second", "1.0.0", second)));

        try (JarFile jar = new JarFile(result.jarPath().toFile())) {
            assertEquals("""
                    com.example.AppPlugin
                    com.example.FirstPlugin
                    com.example.SecondPlugin
                    com.example.SharedPlugin
                    """, readEntry(jar, "META-INF/services/com.example.Plugin"));
            assertEquals("""
                    # Merged by Zolt uber jar packaging
                    netty-buffer.repoStatus=clean
                    netty-buffer.version=4.1.115.Final
                    netty-common.repoStatus=clean
                    netty-common.version=4.1.115.Final
                    """, readEntry(jar, "META-INF/io.netty.versions.properties"));
        }
        assertTrue(result.mergeDecisions().contains(new PackageMergeDecision(
                "service-descriptor",
                "META-INF/services/com.example.Plugin",
                Optional.empty(),
                List.of("application output", "com.example:first", "com.example:second"))));
        assertTrue(result.mergeDecisions().contains(new PackageMergeDecision(
                "netty-version-metadata",
                "META-INF/io.netty.versions.properties",
                Optional.empty(),
                List.of("com.example:first", "com.example:second"))));
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
