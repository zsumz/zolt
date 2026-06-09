package com.zolt.quarkus;

import com.zolt.project.ProjectConfig;
import java.nio.file.Files;
import java.nio.file.Path;

public final class QuarkusTestPlanService {
    private final UnsupportedTestScanner unsupportedTestScanner;

    public QuarkusTestPlanService() {
        this(new QuarkusUnsupportedTestScanner()::scan);
    }

    QuarkusTestPlanService(UnsupportedTestScanner unsupportedTestScanner) {
        if (unsupportedTestScanner == null) {
            throw new QuarkusPlanException("Quarkus unsupported test scanner is required.");
        }
        this.unsupportedTestScanner = unsupportedTestScanner;
    }

    public QuarkusTestPlan plan(Path projectDirectory, ProjectConfig config) {
        if (projectDirectory == null) {
            throw new QuarkusPlanException("Quarkus test plan requires a project directory.");
        }
        if (config == null) {
            throw new QuarkusPlanException("Quarkus test plan requires a project config.");
        }
        if (!config.frameworkSettings().quarkus().enabled()) {
            throw new QuarkusPlanException(
                    "Quarkus is not enabled for this project. "
                            + "Add `[framework.quarkus] enabled = true` to zolt.toml, "
                            + "run `zolt resolve`, then run `zolt quarkus test-plan` again.");
        }
        Path root = projectDirectory.toAbsolutePath().normalize();
        Path testOutputDirectory = root.resolve(config.build().testOutput()).normalize();
        return new QuarkusTestPlan(
                root,
                testOutputDirectory,
                Files.isDirectory(testOutputDirectory),
                root.resolve("target/quarkus/test-application-model.dat").normalize(),
                root.resolve("target/quarkus/zolt-test-bootstrap.properties").normalize(),
                unsupportedTestScanner.scan(testOutputDirectory));
    }

    @FunctionalInterface
    interface UnsupportedTestScanner {
        java.util.List<QuarkusUnsupportedTest> scan(Path testOutputDirectory);
    }
}
