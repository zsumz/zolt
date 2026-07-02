package sh.zolt.doctor;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Shared test {@link JdkChecker} that detects the running JDK once and caches the result, so tests
 * can assert that JDK detection is reused across a build/launch sequence rather than re-read per
 * call. Lives in {@code zolt-build}'s test tree (next to {@link JdkChecker}) so the run-service
 * failure tests can use it locally without depending on the test-execution runtime library — keeping
 * the workspace dependency graph acyclic.
 */
public final class CachingJdkChecker implements JdkChecker {
    private int detectCalls;
    private int toolchainReads;
    private JdkStatus status;

    @Override
    public JdkStatus detect(String requiredVersion) {
        detectCalls++;
        if (status == null) {
            toolchainReads++;
            Path javaHome = Path.of(System.getProperty("java.home"));
            status = new JdkStatus(
                    Optional.of(javaHome),
                    Optional.of(javaHome.resolve("bin").resolve(executable("java"))),
                    Optional.of(javaHome.resolve("bin").resolve(executable("javac"))),
                    Optional.of(javaHome.resolve("bin").resolve(executable("jar"))),
                    Optional.of(requiredVersion),
                    requiredVersion);
        }
        return status;
    }

    public int detectCalls() {
        return detectCalls;
    }

    public int toolchainReads() {
        return toolchainReads;
    }

    private static String executable(String name) {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? name + ".exe"
                : name;
    }
}
