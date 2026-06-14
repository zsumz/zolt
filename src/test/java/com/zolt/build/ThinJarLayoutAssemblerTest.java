package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.PackageId;
import com.zolt.resolve.ResolvedPackage;
import com.zolt.toml.ZoltTomlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ThinJarLayoutAssemblerTest {
    private final ThinJarLayoutAssembler assembler = new ThinJarLayoutAssembler(
            new ManifestGenerator(),
            new ZoltLockfileReader(),
            new ClasspathBuilder());

    @TempDir
    private Path projectDir;

    @Test
    void assemblesThinJarAndRuntimeClasspathFromPrecomputedPackages() throws IOException {
        Path outputDirectory = projectDir.resolve("target/classes");
        Path mainClass = outputDirectory.resolve("com/example/Main.class");
        Files.createDirectories(mainClass.getParent());
        Files.write(mainClass, new byte[] {1});
        Files.writeString(outputDirectory.resolve(".zolt-incremental-main.state"), "state\n");
        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");

        PackageResult result = assembler.assemble(
                projectDir,
                config(),
                new BuildResult(Optional.empty(), 1, 0, outputDirectory, ""),
                jarPath,
                Optional.of(projectDir.resolve("cache")),
                Optional.of(List.of(
                        packageWithScope("com.example", "runtime-lib", DependencyScope.RUNTIME),
                        packageWithScope("com.example", "provided-api", DependencyScope.PROVIDED))));

        assertEquals(PackageMode.THIN, result.mode());
        assertEquals(jarPath, result.jarPath());
        assertEquals(Optional.of(projectDir.resolve("target/demo-0.1.0.runtime-classpath")), result.runtimeClasspathPath());
        assertEquals(1, result.entryCount());
        assertEquals(
                projectDir.resolve("cache/com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar") + "\n",
                Files.readString(result.runtimeClasspathPath().orElseThrow()));
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            assertNotNull(jar.getEntry("META-INF/MANIFEST.MF"));
            assertNotNull(jar.getEntry("com/example/Main.class"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(".zolt-incremental-main.state")));
            Attributes attributes = jar.getManifest().getMainAttributes();
            assertEquals("1.0", attributes.getValue(Attributes.Name.MANIFEST_VERSION));
            assertEquals("com.example.Main", attributes.getValue(Attributes.Name.MAIN_CLASS));
        }
    }

    private ProjectConfig config() {
        return new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                main = "com.example.Main"
                """);
    }

    private ResolvedClasspathPackage packageWithScope(String groupId, String artifactId, DependencyScope scope) {
        return new ResolvedClasspathPackage(
                new ResolvedPackage(
                        new PackageId(groupId, artifactId),
                        "1.0.0",
                        false,
                        projectDir.resolve("cache/%s/%s/1.0.0/%s-1.0.0.pom".formatted(
                                groupId.replace('.', '/'),
                                artifactId,
                                artifactId)),
                        projectDir.resolve("cache/%s/%s/1.0.0/%s-1.0.0.jar".formatted(
                                groupId.replace('.', '/'),
                                artifactId,
                                artifactId))),
                scope);
    }
}
