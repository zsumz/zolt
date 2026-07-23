package sh.zolt.build.cache;

import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.ProjectConfig;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Decides whether a module's compiled output is a pure function of its hashed inputs, and therefore
 * safe to store in and restore from the build-output cache.
 *
 * <p>A {@code cache = "none"} exec generated-source step always runs against an oracle Zolt cannot
 * hash (a live database, a clock, the network), so a module that has one is not hermetic: its output
 * is not reproducible from inputs and must never be cached. This mirrors the reproducibility taint the
 * package evidence writer uses, extended to both the main and test scopes because the cache stores
 * both. Over-approximating taint is the safe direction — a false "not cacheable" only costs a rebuild.
 */
public final class BuildCacheModulePolicy {
    private BuildCacheModulePolicy() {
    }

    public static boolean cacheable(ProjectConfig config) {
        return taintReason(config).isEmpty();
    }

    /** A human-readable reason the module is excluded from the cache, or empty when it is cacheable. */
    public static Optional<String> taintReason(ProjectConfig config) {
        return Stream.concat(
                        config.build().generatedMainSources().stream(),
                        config.build().generatedTestSources().stream())
                .filter(step -> step.kind() == GeneratedSourceKind.EXEC)
                .filter(step -> "none".equals(step.exec().cache()))
                .map(BuildCacheModulePolicy::reason)
                .findFirst();
    }

    private static String reason(GeneratedSourceStep step) {
        return "exec step `" + step.id() + "` uses cache = \"none\" (non-hermetic; output is not a function of inputs)";
    }
}
