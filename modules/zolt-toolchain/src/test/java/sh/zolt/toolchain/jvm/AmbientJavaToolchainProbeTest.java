package sh.zolt.toolchain.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.toolchain.JavaDistribution;
import sh.zolt.project.toolchain.JavaFeature;
import sh.zolt.project.toolchain.JavaToolchainRequest;
import sh.zolt.project.toolchain.ToolchainPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AmbientJavaToolchainProbeTest {
    @TempDir
    private Path tempDir;

    @Test
    void resolvesAmbientJavaFromJavaHome() throws IOException {
        Path javaHome = tempDir.resolve("jdk");
        tool(javaHome, "java");
        tool(javaHome, "javac");
        tool(javaHome, "jar");
        tool(javaHome, "native-image");
        AmbientJavaToolchainProbe probe = probe(
                Map.of("JAVA_HOME", javaHome.toString()),
                java -> Optional.of(new JavaRuntimeInfo(
                        Optional.of("21.0.2"),
                        Optional.of("21"),
                        Optional.of("GraalVM Community"))));

        ResolvedJavaToolchain resolved = probe.resolve(new JavaToolchainRequest(
                "21",
                JavaDistribution.GRAALVM_COMMUNITY,
                Set.of(JavaFeature.NATIVE_IMAGE),
                ToolchainPolicy.PREFER_MANAGED));

        assertTrue(resolved.ok());
        assertEquals(JavaToolchainSource.AMBIENT, resolved.source());
        assertEquals(javaHome, resolved.javaHome().orElseThrow());
        assertEquals(javaHome.resolve("bin/native-image"), resolved.nativeImage().orElseThrow());
    }

    @Test
    void reportsMissingNativeImageWhenRequested() throws IOException {
        Path javaHome = tempDir.resolve("jdk");
        tool(javaHome, "java");
        tool(javaHome, "javac");
        tool(javaHome, "jar");
        AmbientJavaToolchainProbe probe = probe(
                Map.of("JAVA_HOME", javaHome.toString()),
                java -> Optional.of(new JavaRuntimeInfo(
                        Optional.of("21.0.2"),
                        Optional.of("21"),
                        Optional.of("Eclipse Temurin"))));

        ResolvedJavaToolchain resolved = probe.resolve(new JavaToolchainRequest(
                "21",
                JavaDistribution.GRAALVM_COMMUNITY,
                Set.of(JavaFeature.NATIVE_IMAGE),
                ToolchainPolicy.PREFER_MANAGED));

        assertFalse(resolved.ok());
        assertTrue(resolved.problems().stream().anyMatch(problem -> problem.contains("Native Image is missing")));
    }

    @Test
    void reusesAmbientRuntimeProbeAcrossRequests() throws IOException {
        Path javaHome = tempDir.resolve("jdk");
        tool(javaHome, "java");
        tool(javaHome, "javac");
        tool(javaHome, "jar");
        AtomicInteger runtimeReads = new AtomicInteger();
        AmbientJavaToolchainProbe probe = probe(
                Map.of("JAVA_HOME", javaHome.toString()),
                java -> {
                    runtimeReads.incrementAndGet();
                    return Optional.of(new JavaRuntimeInfo(
                            Optional.of("21.0.2"),
                            Optional.of("21"),
                            Optional.of("Eclipse Temurin")));
                });

        ResolvedJavaToolchain java17 = probe.resolve(JavaToolchainRequest.projectDefault("17"));
        ResolvedJavaToolchain java23 = probe.resolve(JavaToolchainRequest.projectDefault("23"));

        assertTrue(java17.ok());
        assertFalse(java23.ok());
        assertEquals(1, runtimeReads.get());
    }

    @Test
    void parsesRuntimeInfoFromJavaOutput() {
        JavaRuntimeInfo info = AmbientJavaToolchainProbe.parseRuntimeInfo("""
                    java.vendor = Eclipse Temurin
                    java.version = 21.0.2
                openjdk version "21.0.2" 2024-01-16
                """);

        assertEquals(Optional.of("21.0.2"), info.version());
        assertEquals(Optional.of("21"), info.featureVersion());
        assertEquals(Optional.of("Eclipse Temurin"), info.vendor());
    }

    private static AmbientJavaToolchainProbe probe(
            Map<String, String> environment,
            AmbientJavaToolchainProbe.RuntimeInfoReader runtimeInfoReader) {
        Function<String, String> env = environment::get;
        return new AmbientJavaToolchainProbe(env, java.io.File.pathSeparator, "Linux", Optional.empty(), runtimeInfoReader);
    }

    private static Path tool(Path javaHomeOrBin, String name) throws IOException {
        Path bin = javaHomeOrBin.getFileName().toString().equals("bin")
                ? javaHomeOrBin
                : javaHomeOrBin.resolve("bin");
        Files.createDirectories(bin);
        Path tool = bin.resolve(name);
        Files.writeString(tool, "");
        tool.toFile().setExecutable(true);
        return tool;
    }
}
