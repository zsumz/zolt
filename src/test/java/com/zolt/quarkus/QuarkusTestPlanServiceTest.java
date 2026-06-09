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
import java.nio.file.Path;
import java.util.List;
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
    void usesSharedScannerForUnsupportedQuarkusTests() {
        Path testOutput = projectDir.toAbsolutePath().normalize().resolve("target/test-classes");
        QuarkusTestPlan plan = new QuarkusTestPlanService(path -> List.of(new QuarkusUnsupportedTest(
                        path.resolve("com/example/HttpTest.class"),
                        Path.of("com/example/HttpTest.class"),
                        "@QuarkusTest")))
                .plan(projectDir, config(true));

        assertEquals(testOutput, plan.testOutputDirectory());
        assertEquals(1, plan.unsupportedTests().size());
        assertEquals(Path.of("com/example/HttpTest.class"), plan.unsupportedTests().getFirst().relativePath());
    }

    @Test
    void rejectsWhenQuarkusIsDisabled() {
        QuarkusPlanException exception = assertThrows(
                QuarkusPlanException.class,
                () -> new QuarkusTestPlanService().plan(projectDir, config(false)));

        assertTrue(exception.getMessage().contains("Quarkus is not enabled for this project"));
        assertTrue(exception.getMessage().contains("zolt quarkus test-plan"));
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
