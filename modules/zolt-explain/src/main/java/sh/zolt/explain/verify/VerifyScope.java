package sh.zolt.explain.verify;

import sh.zolt.dependency.DependencyScope;
import java.util.Optional;

/**
 * The four dependency scopes compared between Maven and Zolt. Enum order is the deterministic
 * report order.
 *
 * <p>The comparison is deliberately restricted to the scopes Maven and Zolt share one-to-one. Maven
 * POM scopes map to these buckets directly ({@code compile}, {@code runtime}, {@code test},
 * {@code provided}); Zolt {@link DependencyScope} values map back the same way. Scopes outside this
 * set are surfaced as informational notes rather than forced into the comparison:
 *
 * <ul>
 *   <li>Maven {@code system} / {@code import} have no Zolt equivalent (import is BOM management, not a
 *       resolved artifact).
 *   <li>Zolt {@code dev}, {@code processor}, {@code test-processor}, {@code quarkus-deployment} and
 *       the {@code tool-*} scopes have no Maven {@code dependency:tree} equivalent (annotation
 *       processors and framework tooling are not emitted as resolved dependencies by Maven).
 * </ul>
 */
public enum VerifyScope {
    COMPILE("compile"),
    RUNTIME("runtime"),
    TEST("test"),
    PROVIDED("provided");

    private final String token;

    VerifyScope(String token) {
        this.token = token;
    }

    /** The lowercase Maven scope token and Zolt lockfile scope name (they agree for these four). */
    public String token() {
        return token;
    }

    /** Maps a Maven {@code dependency:tree} scope token to a compared scope, if it is one of the four. */
    public static Optional<VerifyScope> fromMavenToken(String mavenScope) {
        if (mavenScope == null) {
            return Optional.empty();
        }
        return switch (mavenScope.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "compile" -> Optional.of(COMPILE);
            case "runtime" -> Optional.of(RUNTIME);
            case "test" -> Optional.of(TEST);
            case "provided" -> Optional.of(PROVIDED);
            default -> Optional.empty();
        };
    }

    /** Maps a resolved Zolt {@link DependencyScope} to a compared scope, if it is one of the four. */
    public static Optional<VerifyScope> fromZoltScope(DependencyScope scope) {
        if (scope == null) {
            return Optional.empty();
        }
        return switch (scope) {
            case COMPILE -> Optional.of(COMPILE);
            case RUNTIME -> Optional.of(RUNTIME);
            case TEST -> Optional.of(TEST);
            case PROVIDED -> Optional.of(PROVIDED);
            default -> Optional.empty();
        };
    }
}
