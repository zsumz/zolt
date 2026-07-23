package sh.zolt.plan;

import java.util.Optional;

/**
 * The {@code [toolchain.java.test]} runtime toolchain surfaced in {@code zolt plan --target test}:
 * the Java version tests will run on, whether it is ready, and (when not) the problem + remediation
 * so the plan can reject an unusable test runtime at plan time. The CLI resolves this and passes it
 * in, keeping the plan module free of toolchain dependencies.
 */
public record TestRuntimePlan(
        String version,
        boolean ready,
        Optional<String> problem,
        Optional<String> remediation) {
    public TestRuntimePlan {
        problem = problem == null ? Optional.empty() : problem;
        remediation = remediation == null ? Optional.empty() : remediation;
    }
}
