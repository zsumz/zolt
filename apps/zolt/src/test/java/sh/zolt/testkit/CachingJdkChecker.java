package sh.zolt.testkit;

import sh.zolt.doctor.JdkChecker;
import sh.zolt.doctor.JdkStatus;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

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
