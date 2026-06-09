package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusTestPlanServiceTest {
    @TempDir
    private Path projectDir;

    @Test
    void reportsPlainJUnitPlanWhenCompiledTestOutputIsMissing() {
        QuarkusTestPlan plan = new QuarkusTestPlanService().plan(projectDir, config(true));

        Path root = projectDir.toAbsolutePath().normalize();
        assertEquals(root, plan.projectDirectory());
        assertEquals(root.resolve("target/test-classes"), plan.testOutputDirectory());
        assertEquals(root.resolve("target/quarkus/test-application-model.dat"), plan.serializedApplicationModel());
        assertEquals(root.resolve("target/quarkus/zolt-test-bootstrap.properties"), plan.testRunnerDescriptor());
        assertFalse(plan.testOutputPresent());
        assertTrue(plan.unsupportedTests().isEmpty());
    }

    @Test
    void detectsUnsupportedQuarkusTestAnnotationsDeterministically() throws IOException {
        writeClass("com/example/BetaTest.class", "constant-pool:Lio/quarkus/test/junit/QuarkusIntegrationTest;");
        writeClass("com/example/AlphaTest.class", "constant-pool:Lio/quarkus/test/junit/QuarkusTest;");

        QuarkusTestPlan plan = new QuarkusTestPlanService().plan(projectDir, config(true));

        assertTrue(plan.testOutputPresent());
        assertEquals(2, plan.unsupportedTests().size());
        assertEquals(Path.of("com/example/AlphaTest.class"), plan.unsupportedTests().get(0).relativePath());
        assertEquals("@QuarkusTest", plan.unsupportedTests().get(0).annotationName());
        assertEquals(Path.of("com/example/BetaTest.class"), plan.unsupportedTests().get(1).relativePath());
        assertEquals("@QuarkusIntegrationTest", plan.unsupportedTests().get(1).annotationName());
    }

    @Test
    void rejectsWhenQuarkusIsDisabled() {
        QuarkusPlanException exception = assertThrows(
                QuarkusPlanException.class,
                () -> new QuarkusTestPlanService().plan(projectDir, config(false)));

        assertTrue(exception.getMessage().contains("Quarkus is not enabled for this project"));
        assertTrue(exception.getMessage().contains("zolt quarkus test-plan"));
    }

    private void writeClass(String relativePath, String content) throws IOException {
        Path classFile = projectDir.resolve("target/test-classes").resolve(relativePath);
        Files.createDirectories(classFile.getParent());
        Files.writeString(classFile, content);
    }

    private static ProjectConfig config(boolean quarkusEnabled) {
        return new ProjectConfig(
                        new ProjectMetadata("demo", "1.0.0", "com.example", "21", Optional.empty()),
                        ProjectConfig.defaultRepositories(),
                        Map.of(),
                        Map.of(),
                        BuildSettings.defaults())
                .withFrameworkSettings(new FrameworkSettings(new QuarkusSettings(
                        quarkusEnabled,
                        QuarkusPackageMode.FAST_JAR)));
    }
}
