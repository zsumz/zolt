package com.zolt.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.NativeSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.PublicationMetadata;
import com.zolt.project.QuarkusSettings;
import com.zolt.project.SpringBootSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildPlanServiceTest {
    private final BuildPlanService service = new BuildPlanService();

    @TempDir
    private Path projectDir;

    @Test
    void plansCoveragePackageAndPublishOutputsUnderConfiguredOutputRoot() {
        ProjectConfig config = ProjectConfigs.withDirectDependencies(
                        new ProjectMetadata("demo", "1.0.0", "com.example", "21", Optional.empty()),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        new BuildSettings(
                                "src/main/java",
                                "src/test/java",
                                ".zolt/build",
                                ".zolt/build/classes",
                                ".zolt/build/test-classes"))
                .withPackageSettings(new PackageSettings(
                        PackageMode.THIN,
                        true,
                        true,
                        true,
                        PublicationMetadata.empty()));

        BuildPlan plan = service.plan(projectDir, config, PlanTarget.CI, Optional.empty());

        PlanNode coverage = node(plan, "coverage");
        assertEquals(List.of(".zolt/build/test-classes", "zolt.lock"), coverage.inputs());
        assertEquals(List.of(".zolt/build/coverage"), coverage.outputs());

        PlanNode packageNode = node(plan, "assemble-package");
        assertEquals(List.of(
                ".zolt/build/demo-1.0.0.jar",
                ".zolt/build/demo-1.0.0-sources.jar",
                ".zolt/build/demo-1.0.0-javadoc.jar",
                ".zolt/build/demo-1.0.0-tests.jar"), packageNode.outputs());

        PlanNode publish = node(plan, "publish-dry-run");
        assertTrue(publish.inputs().contains(".zolt/build/demo-1.0.0.jar"));
        assertTrue(publish.inputs().contains("zolt.lock"));
        assertEquals(List.of(".zolt/build/publish"), publish.outputs());
    }

    @Test
    void plansReadySpringBootNativeInputsWithoutExecutingTools() throws IOException {
        ProjectConfig config = springBootNativeConfig();
        Path nativeImage = projectDir.resolve("bin/native-image");
        Files.createDirectories(nativeImage.getParent());
        Files.writeString(nativeImage, "#!/bin/sh\nexit 0\n");
        assertTrue(nativeImage.toFile().setExecutable(true));
        writeLockfileWithSpringAotTooling();
        writeSpringAotOutput(FileTime.fromMillis(2_000));
        writeInputFiles(FileTime.fromMillis(1_000));

        BuildPlan plan = service.plan(
                projectDir,
                config,
                PlanTarget.NATIVE,
                Optional.empty(),
                Optional.of(nativeImage));

        assertFalse(plan.blocked());
        assertEquals(PlanNodeStatus.READY, node(plan, "spring-boot-native-intent").status());
        assertEquals(PlanNodeStatus.READY, node(plan, "spring-aot-tooling").status());
        assertEquals(PlanNodeStatus.READY, node(plan, "spring-aot-output").status());
        assertEquals(PlanNodeStatus.READY, node(plan, "native-image-executable").status());
        assertTrue(node(plan, "spring-aot-output").details().contains("freshness: present"));
        assertTrue(node(plan, "native-image").details().contains("nativeArgs: [--no-fallback]"));
        assertEquals(List.of("target/demo-1.0.0.jar"), node(plan, "native-package-input").outputs());
    }

    @Test
    void plansSpringBootNativeBlockersForMissingToolingStaleAotAndMissingNativeImage() throws IOException {
        ProjectConfig config = springBootNativeConfig();
        writeLockfileWithoutSpringAotTooling();
        writeSpringAotOutput(FileTime.fromMillis(1_000));
        writeInputFiles(FileTime.fromMillis(2_000));

        BuildPlan plan = service.plan(
                projectDir,
                config,
                PlanTarget.NATIVE,
                Optional.empty(),
                Optional.of(projectDir.resolve("missing/native-image")));

        assertTrue(plan.blocked());
        assertBlocker(node(plan, "spring-aot-tooling"), "missing-spring-aot-tooling");
        assertBlocker(node(plan, "spring-aot-output"), "stale-spring-aot-output");
        assertBlocker(node(plan, "native-image-executable"), "missing-native-image");
        assertTrue(node(plan, "spring-aot-output").details().contains("freshness: stale"));
    }

    @Test
    void nativePlanDoesNotRequireSpringAotForPlainJavaProjects() throws IOException {
        ProjectConfig config = ProjectConfigs.withDirectDependencies(
                new ProjectMetadata(
                        "plain",
                        "1.0.0",
                        "com.example",
                        "21",
                        Optional.of("com.example.Main")),
                Map.of(),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
        Path nativeImage = projectDir.resolve("bin/native-image");
        Files.createDirectories(nativeImage.getParent());
        Files.writeString(nativeImage, "#!/bin/sh\nexit 0\n");
        assertTrue(nativeImage.toFile().setExecutable(true));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        BuildPlan plan = service.plan(
                projectDir,
                config,
                PlanTarget.NATIVE,
                Optional.empty(),
                Optional.of(nativeImage));

        assertFalse(plan.blocked());
        assertEquals(PlanNodeStatus.SKIPPED, node(plan, "spring-aot-tooling").status());
        assertEquals(PlanNodeStatus.SKIPPED, node(plan, "spring-aot-output").status());
        assertEquals(PlanNodeStatus.READY, node(plan, "native-package-input").status());
    }

    private static PlanNode node(BuildPlan plan, String id) {
        return plan.nodes().stream()
                .filter(node -> node.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static void assertBlocker(PlanNode node, String code) {
        assertTrue(node.blockers().stream().anyMatch(blocker -> blocker.code().equals(code)));
    }

    private static ProjectConfig springBootNativeConfig() {
        return ProjectConfigs.withDirectDependencies(
                        new ProjectMetadata(
                                "demo",
                                "1.0.0",
                                "com.example",
                                "21",
                                Optional.of("com.example.DemoApplication")),
                        Map.of(),
                        Map.of("org.springframework.boot:spring-boot-starter-web", "3.3.6"),
                        Map.of(),
                        BuildSettings.defaults(),
                        new NativeSettings("demo-native", "target/native", List.of("--no-fallback")))
                .withFrameworkSettings(new FrameworkSettings(new SpringBootSettings(true), QuarkusSettings.defaults()))
                .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT));
    }

    private void writeLockfileWithSpringAotTooling() throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.springframework.boot:spring-boot-starter-web"
                version = "3.3.6"
                source = "maven-central"
                scope = "compile"
                direct = true
                dependencies = []

                [[package]]
                id = "org.springframework.boot:spring-boot"
                version = "3.3.6"
                source = "maven-central"
                scope = "tool-spring-aot"
                direct = false
                dependencies = []
                """);
    }

    private void writeLockfileWithoutSpringAotTooling() throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.springframework.boot:spring-boot-starter-web"
                version = "3.3.6"
                source = "maven-central"
                scope = "compile"
                direct = true
                dependencies = []
                """);
    }

    private void writeSpringAotOutput(FileTime time) throws IOException {
        Path sources = projectDir.resolve("target/spring-aot/main/sources/com/example/Demo__BeanDefinitions.java");
        Path classes = projectDir.resolve("target/spring-aot/main/classes/com/example/Demo__BeanDefinitions.class");
        Path metadata = projectDir.resolve("target/spring-aot/main/resources/META-INF/native-image/com/example/demo");
        Path reflection = metadata.resolve("reflect-config.json");
        Path reachability = metadata.resolve("reachability-metadata.json");
        Files.createDirectories(sources.getParent());
        Files.createDirectories(classes.getParent());
        Files.createDirectories(metadata);
        Files.writeString(sources, "package com.example; final class Demo__BeanDefinitions {}\n");
        Files.writeString(classes, "class-bytes");
        Files.writeString(reflection, "[]\n");
        Files.writeString(reachability, "{}\n");
        for (Path path : List.of(sources, classes, reflection, reachability)) {
            Files.setLastModifiedTime(path, time);
        }
    }

    private void writeInputFiles(FileTime time) throws IOException {
        Path classFile = projectDir.resolve("target/classes/com/example/DemoApplication.class");
        Files.createDirectories(classFile.getParent());
        Files.writeString(projectDir.resolve("zolt.toml"), "[project]\nname = \"demo\"\n");
        Files.writeString(classFile, "class-bytes");
        Files.setLastModifiedTime(projectDir.resolve("zolt.toml"), time);
        Files.setLastModifiedTime(classFile, time);
    }
}
