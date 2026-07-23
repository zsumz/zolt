package sh.zolt.plan;

import sh.zolt.project.BuildSettings;
import sh.zolt.project.TestRuntimeSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the {@code run-tests} plan node, including the resolved {@code [toolchain.java.test]}
 * runtime toolchain: its version is surfaced as a detail, and an unready toolchain blocks the node so
 * {@code zolt plan --target test} rejects it at plan time.
 */
final class BuildPlanRunTestsNode {
    private BuildPlanRunTestsNode() {
    }

    static PlanNode node(BuildSettings build, Optional<Path> reportsDir, Optional<TestRuntimePlan> testRuntime) {
        TestRuntimeSettings runtime = build.testRuntime();
        List<String> outputs = reportsDir.map(path -> List.of(path.toString())).orElseGet(List::of);
        List<String> details = new ArrayList<>();
        testRuntime.ifPresent(toolchain ->
                details.add("testRuntimeJava: " + toolchain.version() + " ([toolchain.java.test])"));
        if (!runtime.jvmArgs().isEmpty()) {
            details.add("jvmArgs: " + runtime.jvmArgs().size());
        }
        if (!runtime.systemProperties().isEmpty()) {
            details.add("systemProperties: " + runtime.systemProperties().keySet());
        }
        if (!runtime.environment().isEmpty()) {
            details.add("environment: " + runtime.environment().keySet() + " (values redacted)");
        }
        if (!runtime.events().isEmpty()) {
            details.add("events: " + String.join(",", runtime.events()));
        }
        List<PlanBlocker> blockers = new ArrayList<>();
        testRuntime.filter(toolchain -> !toolchain.ready()).ifPresent(toolchain -> blockers.add(new PlanBlocker(
                "test-runtime-toolchain",
                toolchain.problem().orElse("Test runtime toolchain " + toolchain.version() + " is not ready."),
                toolchain.remediation().orElse("Run `zolt toolchain sync`."))));
        return new PlanNode(
                "run-tests",
                "test",
                blockers.isEmpty() ? PlanNodeStatus.READY : PlanNodeStatus.BLOCKED,
                "Run tests through Zolt's JUnit Platform path.",
                List.of(build.testOutput(), build.output(), "zolt.lock"),
                outputs,
                details,
                blockers);
    }
}
