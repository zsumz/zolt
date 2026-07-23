package sh.zolt.build.generatedsource;

import sh.zolt.build.BuildException;
import sh.zolt.build.generatedsource.ExecGeneratedSourceService.ProcessResult;
import sh.zolt.build.generatedsource.ExecGeneratedSourceService.ProcessRunner;
import sh.zolt.project.ExecToolSettings;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Resolves a {@code process} runner tool: discovers the binary on the curated PATH, probes the
 * {@code versionCommand} (whose stdout becomes the tool identity in the step fingerprint), and enforces
 * the optional {@code versionExpect} semver-range guard with a fail-fast actionable error on drift.
 */
final class ExecProcessToolResolver {
    private ExecProcessToolResolver() {
    }

    static Resolved resolve(
            ExecToolSettings tool,
            String subject,
            Path probeDirectory,
            UnaryOperator<String> ambientEnv,
            String pathSeparator,
            ProcessRunner processRunner,
            Duration timeout) {
        String pathValue = ambientEnv.apply("PATH");
        Path binary = ProcessToolLocator.locate(tool.binary(), pathValue, pathSeparator, subject);
        List<String> versionCommand = tool.versionCommand();
        Path probeBinary = ProcessToolLocator.locate(versionCommand.getFirst(), pathValue, pathSeparator, subject);
        List<String> probeCommand = new ArrayList<>();
        probeCommand.add(probeBinary.toString());
        probeCommand.addAll(versionCommand.subList(1, versionCommand.size()));

        ProcessResult result = processRunner.run(probeCommand, probeDirectory, probeEnvironment(ambientEnv), timeout);
        if (result.timedOut()) {
            throw BuildException.actionable(
                    "Exec step " + subject + " version probe `" + String.join(" ", versionCommand)
                            + "` did not complete within " + timeout.toSeconds() + "s.",
                    "Check that `" + tool.binary() + "` responds to its versionCommand, or raise timeoutSeconds.");
        }
        if (result.exitCode() != 0) {
            throw BuildException.actionable(
                    "Exec step " + subject + " version probe `" + String.join(" ", versionCommand)
                            + "` failed with exit code " + result.exitCode() + ".",
                    "Fix the versionCommand or ensure `" + tool.binary() + "` is installed and runnable.");
        }
        String probedVersion = result.output().strip();
        tool.versionExpect().ifPresent(expect -> enforceVersionExpect(subject, tool, expect, probedVersion));
        return new Resolved(binary, probedVersion);
    }

    private static void enforceVersionExpect(
            String subject, ExecToolSettings tool, String expect, String probedVersion) {
        String actual = ExecVersionRange.extractVersion(probedVersion).orElseThrow(() -> BuildException.actionable(
                "Exec step " + subject + " could not read a version from `" + tool.binary()
                        + "` probe output to check versionExpect `" + expect + "`.",
                "Adjust versionCommand so its stdout contains the tool version, or remove versionExpect."));
        if (!ExecVersionRange.parse(expect).matches(actual)) {
            throw BuildException.actionable(
                    "Exec tool `" + tool.binary() + "` for " + subject + " reports version " + actual
                            + ", which does not satisfy versionExpect `" + expect + "`.",
                    "Install a `" + tool.binary() + "` version matching `" + expect + "`, or update versionExpect.");
        }
    }

    private static Map<String, String> probeEnvironment(UnaryOperator<String> ambientEnv) {
        Map<String, String> environment = new LinkedHashMap<>();
        putIfPresent(environment, "PATH", ambientEnv.apply("PATH"));
        putIfPresent(environment, "HOME", ambientEnv.apply("HOME"));
        return environment;
    }

    private static void putIfPresent(Map<String, String> environment, String name, String value) {
        if (value != null && !value.isBlank()) {
            environment.put(name, value);
        }
    }

    record Resolved(Path binary, String probedVersion) {
    }
}
