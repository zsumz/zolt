package sh.zolt.explain.verify;

/**
 * The Maven-style coordinates ({@code group}, {@code artifact}, {@code version}) of one Gradle project,
 * keyed elsewhere by its Gradle project path ({@code ":"} for the root, {@code ":app"} for an included
 * project). {@link GradleDependencyTreeParser} uses this both to give a parsed project block its module
 * identity and to resolve {@code project :x} dependency lines to a concrete coordinate, mirroring how
 * Maven's {@code dependency:tree} lists reactor siblings with their full coordinate.
 *
 * <p>These are derived by static inspection of the Gradle build, so {@code group} or {@code version}
 * may be empty when a project sets them through cross-project configuration the static inspector cannot
 * evaluate; the empty value is carried through honestly rather than guessed.
 */
public record GradleProjectCoordinates(String group, String artifact, String version) {

    public GradleProjectCoordinates {
        group = group == null ? "" : group.trim();
        artifact = artifact == null ? "" : artifact.trim();
        version = version == null ? "" : version.trim();
    }

    /** Join key against the other side: {@code group:artifact}. */
    public String moduleKey() {
        return group + ":" + artifact;
    }
}
