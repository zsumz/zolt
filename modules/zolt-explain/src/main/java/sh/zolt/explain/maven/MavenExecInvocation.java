package sh.zolt.explain.maven;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One statically extracted exec-shaped plugin invocation ({@code exec-maven-plugin} {@code java}/{@code
 * exec} goal, {@code frontend-maven-plugin} goal, or a {@code maven-antrun-plugin} {@code run} target),
 * captured so {@code zolt explain --emit-toml} can draft an equivalent {@code kind = "exec"} step.
 *
 * <p>Every field is best-effort from the POM {@code <configuration>}; values Zolt could not read
 * statically stay empty. Two flags record why an invocation is <em>not</em> mappable to an argv-array
 * exec step: {@code shellUnsafe} means an argument carries shell metacharacters ({@code &&}, {@code |},
 * {@code $(...)}) that an argv array cannot express, and {@code controlFlow} means an antrun target is
 * more than a single command (conditions, loops, or multiple ant tasks). Either flag downgrades the
 * draft to a migration blocker rather than a silent, wrong mapping.
 */
public record MavenExecInvocation(
        String executionId,
        String goal,
        Optional<String> phase,
        Optional<String> mainClass,
        Optional<String> executable,
        List<String> arguments,
        Optional<String> workingDirectory,
        Map<String, String> environmentVariables,
        List<String> executableDependencies,
        boolean shellUnsafe,
        boolean controlFlow) {
    public MavenExecInvocation {
        executionId = executionId == null ? "" : executionId;
        goal = goal == null ? "" : goal;
        phase = phase == null ? Optional.empty() : phase;
        mainClass = mainClass == null ? Optional.empty() : mainClass;
        executable = executable == null ? Optional.empty() : executable;
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        workingDirectory = workingDirectory == null ? Optional.empty() : workingDirectory;
        environmentVariables = environmentVariables == null || environmentVariables.isEmpty()
                ? Map.of()
                : Map.copyOf(new TreeMap<>(environmentVariables));
        executableDependencies = executableDependencies == null ? List.of() : List.copyOf(executableDependencies);
    }

    /** A single, statically expressible command (no shell metacharacters, no antrun control flow). */
    public boolean mappable() {
        return !shellUnsafe && !controlFlow;
    }
}
