package sh.zolt.update;

import java.util.List;
import java.util.Optional;

/**
 * One reportable row: a single zolt.toml surface, its current version, the discovered update
 * targets, and advisory context. {@code identifier} is the alias name for {@link
 * OutdatedSurface#VERSION_ALIAS} and {@code group:artifact} otherwise. {@code governs} lists the
 * coordinates a version alias governs (empty for other surfaces). {@code members} lists the
 * workspace members that share this surface (empty outside a workspace).
 */
public record OutdatedEntry(
        OutdatedSurface surface,
        String identifier,
        String section,
        String currentVersion,
        OutdatedStatus status,
        OutdatedCandidates candidates,
        Optional<String> sourceRepository,
        List<String> governs,
        List<String> members,
        List<String> notes) {
    public OutdatedEntry {
        governs = governs == null ? List.of() : List.copyOf(governs);
        members = members == null ? List.of() : List.copyOf(members);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    OutdatedEntry withMembers(List<String> updatedMembers) {
        return new OutdatedEntry(
                surface,
                identifier,
                section,
                currentVersion,
                status,
                candidates,
                sourceRepository,
                governs,
                updatedMembers,
                notes);
    }
}
