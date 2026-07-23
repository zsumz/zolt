package sh.zolt.build.cache;

import sh.zolt.doctor.JdkStatus;

/**
 * The resolved JDK identity folded into a build-cache key.
 *
 * <p>javac can emit different bytecode across JDK majors even for the same {@code --release} target, so
 * the compiling JDK must be part of the key. Only {@link JdkStatus} reaches the compile site today, and
 * it carries the (major-level) version but not the vendor; the version string is a safe, conservative
 * identity: a false miss only rebuilds, and two genuinely different compilers almost never share a
 * version string. The compile site validates the JDK before this runs, so the version is present.
 */
public final class BuildCacheJdkIdentity {
    private BuildCacheJdkIdentity() {
    }

    public static String of(JdkStatus jdkStatus) {
        return jdkStatus.version()
                .or(() -> jdkStatus.featureVersion().map(String::valueOf))
                .filter(value -> !value.isBlank())
                .orElse("unknown");
    }
}
