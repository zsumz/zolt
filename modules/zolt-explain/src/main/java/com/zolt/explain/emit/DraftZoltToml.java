package com.zolt.explain.emit;

import com.zolt.project.ProjectConfig;
import java.util.List;

/**
 * A draft zolt.toml synthesized from a static migration audit.
 *
 * <p>{@link #config()} is the mappable part of the audit rendered as real TOML. {@link #notes()} are
 * ambiguous or unmappable inspection facts the audit could not safely guess; the renderer emits them
 * as {@code #}-prefixed comment lines so the adopter can resolve them by hand before use.
 *
 * <p>A single-project audit emits one {@code DraftZoltToml}; a multi-module reactor / multi-project
 * build emits a {@link DraftWorkspace} instead. Both are {@link DraftEmit}s.
 */
public record DraftZoltToml(ProjectConfig config, List<String> notes) implements DraftEmit {
    public DraftZoltToml {
        notes = List.copyOf(notes);
    }
}
