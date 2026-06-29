package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.PackageId;
import com.zolt.quarkus.bootstrap.QuarkusApplicationArtifact;
import com.zolt.quarkus.bootstrap.QuarkusBootstrapDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusAnnotationLauncherClasspathPlannerTest {
    @TempDir
    private Path tempDir;

    @Test
    void preservesFullLauncherClasspathAndClassifiesSharedBuilderArtifact() {
        Path quarkusBuilder = Path.of("/cache/io/quarkus/quarkus-builder-3.33.2.jar");
        Path junitConsole = Path.of("/cache/org/junit/platform/junit-platform-console.jar");
        QuarkusAnnotationLauncherClasspathPlanner planner = new QuarkusAnnotationLauncherClasspathPlanner(
                descriptorFile -> bootstrapDescriptor(List.of(
                        quarkusBuilder,
                        Path.of("/cache/io/quarkus/quarkus-deployment-3.33.2.jar"))));

        QuarkusAnnotationLauncherClasspathPlan plan = planner.plan(descriptor(List.of(
                Path.of("/repo/target/test-classes"),
                quarkusBuilder,
                junitConsole)));

        assertEquals(List.of(Path.of("/repo/target/test-classes"), quarkusBuilder, junitConsole), plan.launcherClasspath());
        assertEquals(plan.launcherClasspath(), plan.junitDiscoveryClasspath());
        assertEquals(List.of(), plan.serviceFilteredArtifacts());
        assertEquals(List.of(quarkusBuilder.toAbsolutePath().normalize()), plan.splitSensitiveArtifacts());
        assertTrue(plan.builderApiVisible());
        assertEquals(1, plan.sharedDeploymentEntries());
    }

    @Test
    void filtersQuarkusJunitConfigLauncherSessionServiceForAnnotationRunner() throws IOException {
        Path quarkusJunitConfig = tempDir.resolve("quarkus-junit-config-3.33.2.jar");
        writeJar(
                quarkusJunitConfig,
                List.of(
                        "io/quarkus/test/config/QuarkusClassOrderer.class",
                        "META-INF/services/org.junit.platform.launcher.LauncherSessionListener",
                        "META-INF/services/io.smallrye.config.SmallRyeConfigBuilderCustomizer"));
        QuarkusAnnotationLauncherClasspathPlanner planner = new QuarkusAnnotationLauncherClasspathPlanner(
                descriptorFile -> bootstrapDescriptor(List.of()));

        QuarkusAnnotationLauncherClasspathPlan plan = planner.plan(descriptor(
                List.of(Path.of("/repo/target/test-classes"), quarkusJunitConfig),
                tempDir.resolve("target/quarkus/zolt-test-bootstrap.properties")));

        Path filteredJar = tempDir.resolve("target/quarkus/annotation-runner/service-filtered-quarkus-junit-config-3.33.2.jar");
        assertEquals(List.of(Path.of("/repo/target/test-classes"), filteredJar), plan.launcherClasspath());
        assertEquals(plan.launcherClasspath(), plan.junitDiscoveryClasspath());
        assertEquals(List.of(filteredJar), plan.serviceFilteredArtifacts());
        try (JarFile jar = new JarFile(filteredJar.toFile())) {
            assertTrue(jar.getEntry("io/quarkus/test/config/QuarkusClassOrderer.class") != null);
            assertTrue(jar.getEntry("META-INF/services/io.smallrye.config.SmallRyeConfigBuilderCustomizer") != null);
            assertTrue(jar.getEntry("META-INF/services/org.junit.platform.launcher.LauncherSessionListener") == null);
        }
    }

    @Test
    void augmentsQuarkusJunitWithZoltTestClassBeanCustomizer() throws IOException {
        Path quarkusJunit = tempDir.resolve("quarkus-junit-3.33.2.jar");
        writeJar(
                quarkusJunit,
                List.of(
                        "io/quarkus/test/junit/TestBuildChainFunction.class",
                        "META-INF/services/io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer"));
        QuarkusAnnotationLauncherClasspathPlanner planner = new QuarkusAnnotationLauncherClasspathPlanner(
                descriptorFile -> bootstrapDescriptor(List.of()));

        QuarkusAnnotationLauncherClasspathPlan plan = planner.plan(descriptor(
                List.of(Path.of("/repo/target/test-classes"), quarkusJunit),
                tempDir.resolve("target/quarkus/zolt-test-bootstrap.properties")));

        Path augmentedJar = tempDir.resolve("target/quarkus/annotation-runner/zolt-augmented-quarkus-junit-3.33.2.jar");
        assertEquals(List.of(Path.of("/repo/target/test-classes"), augmentedJar), plan.launcherClasspath());
        assertEquals(List.of(augmentedJar), plan.serviceFilteredArtifacts());
        try (JarFile jar = new JarFile(augmentedJar.toFile())) {
            assertTrue(jar.getEntry("io/quarkus/test/junit/TestBuildChainFunction.class") != null);
            assertTrue(jar.getEntry("com/zolt/quarkus/ZoltQuarkusTestClassBeanCustomizer.class") != null);
            JarEntry service = jar.getJarEntry(
                    "META-INF/services/io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer");
            assertTrue(service != null);
            String serviceContent = new String(jar.getInputStream(service).readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("com.zolt.quarkus.ZoltQuarkusTestClassBeanCustomizer\n", serviceContent);
        }
    }

    @Test
    void reportsBuilderApiVisibilityWithoutDeploymentOverlap() {
        Path quarkusBuilder = Path.of("/cache/io/quarkus/quarkus-builder-3.33.2.jar");
        QuarkusAnnotationLauncherClasspathPlanner planner = new QuarkusAnnotationLauncherClasspathPlanner(
                descriptorFile -> bootstrapDescriptor(List.of(Path.of("/cache/io/quarkus/quarkus-deployment-3.33.2.jar"))));

        QuarkusAnnotationLauncherClasspathPlan plan = planner.plan(descriptor(List.of(
                Path.of("/repo/target/test-classes"),
                quarkusBuilder)));

        assertTrue(plan.builderApiVisible());
        assertEquals(plan.launcherClasspath(), plan.junitDiscoveryClasspath());
        assertEquals(List.of(), plan.serviceFilteredArtifacts());
        assertEquals(List.of(), plan.splitSensitiveArtifacts());
        assertEquals(0, plan.sharedDeploymentEntries());
    }

    @Test
    void keepsLauncherClasspathWhenBootstrapDescriptorCannotBeInspected() {
        QuarkusAnnotationLauncherClasspathPlanner planner = new QuarkusAnnotationLauncherClasspathPlanner(
                descriptorFile -> {
                    throw new QuarkusAugmentationException("missing descriptor");
                });

        QuarkusAnnotationLauncherClasspathPlan plan = planner.plan(descriptor(List.of(
                Path.of("/repo/target/test-classes"),
                Path.of("/cache/org/junit/platform/junit-platform-console.jar"))));

        assertEquals(
                List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/cache/org/junit/platform/junit-platform-console.jar")),
                plan.launcherClasspath());
        assertEquals(plan.launcherClasspath(), plan.junitDiscoveryClasspath());
        assertEquals(List.of(), plan.serviceFilteredArtifacts());
        assertFalse(plan.builderApiVisible());
        assertEquals(List.of(), plan.splitSensitiveArtifacts());
        assertEquals(0, plan.sharedDeploymentEntries());
    }

    private static QuarkusTestRunnerDescriptor descriptor(List<Path> testRuntimeClasspath) {
        return descriptor(
                testRuntimeClasspath,
                Path.of("/repo/target/quarkus/zolt-test-bootstrap.properties"));
    }

    private static QuarkusTestRunnerDescriptor descriptor(
            List<Path> testRuntimeClasspath,
            Path descriptorFile) {
        return new QuarkusTestRunnerDescriptor(
                descriptorFile,
                Path.of("/repo/target/quarkus/test-runtime-classpath.txt"),
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/test-classes"),
                Path.of("/repo/target/quarkus/test-application-model.dat"),
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                QuarkusTestRunnerRequest.RUNNER_MODE,
                true,
                true,
                testRuntimeClasspath);
    }

    private static void writeJar(Path jar, List<String> entries) throws IOException {
        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String entry : entries) {
                JarEntry jarEntry = new JarEntry(entry);
                jarEntry.setTime(0L);
                output.putNextEntry(jarEntry);
                output.write(("entry:" + entry).getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static QuarkusBootstrapDescriptor bootstrapDescriptor(List<Path> deploymentClasspath) {
        return new QuarkusBootstrapDescriptor(
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                Path.of("/repo/target/quarkus/runtime-classpath.txt"),
                Path.of("/repo/target/quarkus/deployment-classpath.txt"),
                Path.of("/repo/target/quarkus/platform-properties.txt"),
                Path.of("/repo/target/quarkus/application-model.properties"),
                "io.quarkus.bootstrap.app.QuarkusBootstrap",
                "io.quarkus.bootstrap.app.AugmentAction",
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/quarkus"),
                Path.of("/repo/target/quarkus-app"),
                "fast-jar",
                "sha256:" + "1".repeat(64),
                new QuarkusApplicationArtifact(new PackageId("com.example", "demo"), "1.0.0", Path.of("/repo/target/classes")),
                List.of(Path.of("/repo/target/classes")),
                deploymentClasspath,
                List.of(),
                List.of());
    }
}
