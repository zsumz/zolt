package sh.zolt.explain.emit;

import sh.zolt.project.ProjectConfig;
import java.util.List;

/**
 * A draft zolt.toml synthesized from a static migration audit.
 *
 * <p>{@link #config()} is the mappable part of the audit rendered as real TOML. {@link #notes()} are
 * ambiguous or unmappable inspection facts the audit could not safely guess; the renderer emits them
 * as {@code #}-prefixed comment lines so the adopter can resolve them by hand before use.
 * {@link #commentedProjectKeys()} names project assignments that should stay visible but commented
 * because their inspected value is a review hint, not a safe live setting.
 *
 * <p>{@link #suggestCompilerPlatformApiHost()} marks a  host-platform-API candidate: the POM
 * used {@code source}/{@code target} below the build JDK, so the renderer adds a commented-out
 * {@code # platformApi = "host"} suggestion under {@code [compiler]}. It stays commented because Zolt
 * defaults to the reproducible {@code --release} surface and host mode is opt-in only.
 *
 * <p>A single-project audit emits one {@code DraftZoltToml}; a multi-module reactor / multi-project
 * build emits a {@link DraftWorkspace} instead. Both are {@link DraftEmit}s.
 */
public record DraftZoltToml(
        ProjectConfig config,
        List<String> notes,
        List<String> commentedProjectKeys,
        boolean suggestCompilerPlatformApiHost) implements DraftEmit {
    public DraftZoltToml(ProjectConfig config, List<String> notes) {
        this(config, notes, List.of(), false);
    }

    public DraftZoltToml(ProjectConfig config, List<String> notes, List<String> commentedProjectKeys) {
        this(config, notes, commentedProjectKeys, false);
    }

    public DraftZoltToml {
        notes = List.copyOf(notes);
        commentedProjectKeys = List.copyOf(commentedProjectKeys);
    }
}
