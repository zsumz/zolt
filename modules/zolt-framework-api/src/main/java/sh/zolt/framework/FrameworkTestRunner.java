package sh.zolt.framework;

import sh.zolt.project.ProjectConfig;
import java.util.Optional;

@FunctionalInterface
public interface FrameworkTestRunner {
    Optional<FrameworkTestRunResult> runIfEnabled(FrameworkTestRunRequest request);

    default boolean isEnabled(ProjectConfig config) {
        return false;
    }

    default String testRunnerName() {
        return "framework-test-worker";
    }

    default Optional<String> unsupportedReportsMessage() {
        return Optional.empty();
    }

    static FrameworkTestRunner none() {
        return request -> Optional.empty();
    }
}
