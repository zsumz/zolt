package sh.zolt.toolchain.install;

import java.net.URI;
import java.util.Optional;

/**
 * Rewrites bundled Java toolchain download URLs so JDK archives can be fetched from an internal
 * mirror instead of github.com. This is a transport concern only: the canonical upstream URL is
 * what lands in zolt.lock, and SHA-256 verification runs against the mirrored bytes unchanged.
 *
 * <p>The mirror base replaces the {@code https://github.com} prefix, so a mirror base of
 * {@code https://nexus.example.com/github} turns
 * {@code https://github.com/adoptium/temurin21-binaries/...} into
 * {@code https://nexus.example.com/github/adoptium/temurin21-binaries/...}.
 */
public final class ToolchainDownloadMirror {
    /** Environment variable naming the mirror base URL that replaces github.com. */
    public static final String MIRROR_ENV = "ZOLT_TOOLCHAIN_MIRROR";

    private static final String GITHUB_PREFIX = "https://github.com";

    private final Optional<String> mirrorBase;

    private ToolchainDownloadMirror(Optional<String> mirrorBase) {
        this.mirrorBase = mirrorBase;
    }

    public static ToolchainDownloadMirror none() {
        return new ToolchainDownloadMirror(Optional.empty());
    }

    public static ToolchainDownloadMirror of(String mirrorBase) {
        if (mirrorBase == null || mirrorBase.isBlank()) {
            return none();
        }
        String normalized = mirrorBase.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return new ToolchainDownloadMirror(Optional.of(normalized));
    }

    public static ToolchainDownloadMirror fromEnvironment() {
        return of(System.getenv(MIRROR_ENV));
    }

    public URI rewrite(URI original) {
        if (mirrorBase.isEmpty() || original == null) {
            return original;
        }
        String value = original.toString();
        if (!value.startsWith(GITHUB_PREFIX)) {
            return original;
        }
        return URI.create(mirrorBase.orElseThrow() + value.substring(GITHUB_PREFIX.length()));
    }
}
