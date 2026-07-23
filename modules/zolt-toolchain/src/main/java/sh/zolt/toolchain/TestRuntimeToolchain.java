package sh.zolt.toolchain;

import sh.zolt.error.ActionableException;
import sh.zolt.project.toolchain.JavaToolchainRequest;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The resolved {@code [toolchain.java.test]} runtime toolchain: the JDK Zolt uses to <em>run</em>
 * tests, while compilation stays on the main {@code [toolchain.java]}. Carries the parsed request,
 * its resolution {@link JavaToolchainStatus}, and the compiled {@code [project].java} release so
 * callers (execution, {@code zolt plan --target test}, {@code zolt doctor}) can validate and surface
 * it uniformly.
 */
public record TestRuntimeToolchain(
        JavaToolchainRequest request,
        JavaToolchainStatus status,
        String projectRelease) {

    /**
     * The version-floor problem when the requested runtime is older than the compiled bytecode
     * target. Running newer bytecode on an older JVM throws {@code UnsupportedClassVersionError}, so
     * this is a hard configuration error.
     */
    public Optional<String> releaseProblem() {
        Optional<Integer> testFeature = featureNumber(request.version());
        Optional<Integer> projectFeature = featureNumber(projectRelease);
        if (testFeature.isPresent()
                && projectFeature.isPresent()
                && testFeature.orElseThrow() < projectFeature.orElseThrow()) {
            return Optional.of(
                    "Test runtime Java " + request.version()
                            + " is older than the compiled [project].java release " + projectRelease
                            + "; tests would fail with UnsupportedClassVersionError.");
        }
        return Optional.empty();
    }

    /**
     * True only when the resolved JDK's feature version EXACTLY matches the requested version. This
     * rejects a newer ambient JDK (e.g. 21) silently satisfying an older request (e.g. 17), which
     * would defeat the purpose of proving the code runs on the target JRE.
     */
    public boolean exactVersionMatch() {
        Optional<Integer> resolved =
                status.resolved().runtime().featureVersion().flatMap(TestRuntimeToolchain::featureNumber);
        Optional<Integer> requested = featureNumber(request.version());
        return resolved.isPresent()
                && requested.isPresent()
                && resolved.orElseThrow().equals(requested.orElseThrow());
    }

    /** True when the runtime toolchain is installed, usable, version-exact, and floor-valid. */
    public boolean ready() {
        return releaseProblem().isEmpty()
                && status.ok()
                && exactVersionMatch()
                && status.resolved().java().isPresent();
    }

    /** The resolved java executable when ready; empty otherwise. */
    public Optional<Path> java() {
        return ready() ? status.resolved().java() : Optional.empty();
    }

    /** A human-readable problem when the test runtime toolchain is not ready, for diagnostics/plan. */
    public Optional<String> problem() {
        Optional<String> release = releaseProblem();
        if (release.isPresent()) {
            return release;
        }
        if (ready()) {
            return Optional.empty();
        }
        return Optional.of("Test runtime Java toolchain " + request.version() + " is not installed"
                + mismatchSuffix() + ".");
    }

    /** The remediation matching {@link #problem()}; empty when ready. */
    public Optional<String> remediation() {
        if (releaseProblem().isPresent()) {
            return Optional.of("Set [toolchain.java.test].version to " + projectRelease
                    + " or newer, or lower [project].java.");
        }
        if (ready()) {
            return Optional.empty();
        }
        return Optional.of("Run `zolt toolchain sync` to install Java " + request.version()
                + " for the test runtime, or remove [toolchain.java.test].");
    }

    /**
     * The resolved java executable, or an {@link ActionableException} explaining how to make the
     * test runtime toolchain ready. The not-installed guidance mirrors the main toolchain's
     * {@code zolt toolchain sync} remediation.
     */
    public Path requireJava() {
        Optional<String> problem = problem();
        if (problem.isPresent()) {
            throw new ActionableException(problem.orElseThrow(), remediation().orElseThrow());
        }
        return status.resolved().java().orElseThrow();
    }

    private String mismatchSuffix() {
        if (exactVersionMatch()) {
            return "";
        }
        return status.resolved().runtime().featureVersion()
                .map(resolved -> " (resolved Java " + resolved + " does not match)")
                .orElse("");
    }

    private static Optional<Integer> featureNumber(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.strip();
        if (normalized.startsWith("1.")) {
            normalized = normalized.substring(2);
        }
        int end = 0;
        while (end < normalized.length() && Character.isDigit(normalized.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(normalized.substring(0, end)));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
