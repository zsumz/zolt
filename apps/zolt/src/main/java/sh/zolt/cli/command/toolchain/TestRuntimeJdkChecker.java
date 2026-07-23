package sh.zolt.cli.command.toolchain;

import sh.zolt.doctor.JdkChecker;
import sh.zolt.doctor.JdkStatus;
import sh.zolt.toolchain.TestRuntimeToolchain;
import sh.zolt.toolchain.jvm.ResolvedJavaToolchain;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A {@link JdkChecker} that reports the resolved {@code [toolchain.java.test]} runtime toolchain, so
 * the test worker (and jacoco agent, and integration-test worker) fork on the target JRE. Only the
 * RUN side of {@code TestRunService} uses this; compilation keeps the build toolchain. Resolution
 * and validation happen eagerly in {@link #of} so an unready test toolchain fails before compiling.
 */
public final class TestRuntimeJdkChecker implements JdkChecker {
    private final Path java;
    private final ResolvedJavaToolchain resolved;

    private TestRuntimeJdkChecker(Path java, ResolvedJavaToolchain resolved) {
        this.java = java;
        this.resolved = resolved;
    }

    public static TestRuntimeJdkChecker of(TestRuntimeToolchain toolchain) {
        Path java = toolchain.requireJava();
        return new TestRuntimeJdkChecker(java, toolchain.status().resolved());
    }

    @Override
    public JdkStatus detect(String requiredVersion) {
        return new JdkStatus(
                resolved.javaHome().map(TestRuntimeJdkChecker::absolute),
                Optional.of(absolute(java)),
                resolved.javac().map(TestRuntimeJdkChecker::absolute),
                resolved.jar().map(TestRuntimeJdkChecker::absolute),
                resolved.runtime().featureVersion(),
                requiredVersion);
    }

    private static Path absolute(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
