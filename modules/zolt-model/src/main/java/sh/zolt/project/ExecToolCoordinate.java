package sh.zolt.project;

import java.util.Optional;

/**
 * One Maven coordinate contributing to a named exec tool's classpath closure. {@code version} is the
 * resolved version; {@code versionRef} retains the {@code [versions]} alias when one was used so the
 * writer can round-trip it.
 */
public record ExecToolCoordinate(
        String coordinate,
        Optional<String> version,
        Optional<String> versionRef) {
    public ExecToolCoordinate {
        version = version == null ? Optional.empty() : version;
        versionRef = versionRef == null ? Optional.empty() : versionRef;
    }
}
