package com.zolt.provenance;

import java.util.Optional;

/**
 * Git state read directly from the {@code .git} directory, without shelling out to a {@code git}
 * subprocess.
 *
 * <p>Every field is best-effort: when a value cannot be determined (not a repository, malformed
 * {@code HEAD}, unresolvable ref) the corresponding {@link Optional} is empty. {@code dirty} is
 * {@link Optional#empty()} ("unknown") in v1 — Zolt never claims a clean tree without proof, and a
 * native worktree/index diff is a later followUp.
 */
public record GitProvenance(
        Optional<String> commitSha,
        Optional<String> shortSha,
        Optional<String> branch,
        boolean detached,
        Optional<Boolean> dirty) {

    public GitProvenance {
        commitSha = commitSha == null ? Optional.empty() : commitSha;
        shortSha = shortSha == null ? Optional.empty() : shortSha;
        branch = branch == null ? Optional.empty() : branch;
        dirty = dirty == null ? Optional.empty() : dirty;
    }

    /** Provenance with no git information; a well-formed empty value for the not-a-repo case. */
    public static GitProvenance none() {
        return new GitProvenance(Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.empty());
    }
}
