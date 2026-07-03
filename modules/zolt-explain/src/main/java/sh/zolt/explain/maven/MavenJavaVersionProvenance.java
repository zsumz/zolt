package sh.zolt.explain.maven;

/**
 * Which Maven compiler key resolved a project's main Java level, recorded so the  emit and
 * audit paths can distinguish reproducible {@code <release>} intent from host-JDK
 * {@code source}/{@code target} intent instead of collapsing them into one anonymous version string.
 */
public enum MavenJavaVersionProvenance {
    /** {@code <release>} or {@code maven.compiler.release}: already reproducible-API intent. */
    RELEASE,
    /** {@code <source>}/{@code <target>} or their properties: host-JDK platform-API intent. */
    SOURCE_TARGET,
    /** {@code java.version} property only: ambiguous, treated as strict by default. */
    PROPERTY,
    /** No Java level could be resolved. */
    UNKNOWN
}
