package com.zolt.explain.emit;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps a member's own identity to its relative workspace path, so a dependency that points at a
 * sibling module can be rewritten to {@code { workspace = "<member-path>" }} instead of being emitted
 * as an external coordinate.
 *
 * <p>Maven members register their {@code groupId:artifactId}; a sibling dep coordinate
 * ({@code groupId:artifactId}, the version stripped) is looked up here. Gradle members register the
 * Gradle project path derived from their directory (e.g. {@code lib} for {@code project(":lib")}); a
 * {@code project(...)} notation is normalized to that path and looked up here.
 */
final class WorkspaceMemberRegistry {
    private final Map<String, String> byKey = new HashMap<>();

    void register(String key, String memberPath) {
        if (key != null && !key.isBlank() && memberPath != null && !memberPath.isBlank()) {
            byKey.put(key, memberPath);
        }
    }

    /** The member path a key resolves to, or {@code null} when the key is not a workspace member. */
    String pathFor(String key) {
        return key == null ? null : byKey.get(key);
    }
}
