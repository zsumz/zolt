package sh.zolt.explain.emit;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps a member's own identity to the facts needed to emit a workspace dependency, so a dependency
 * that points at a sibling module can be rewritten to {@code "group:name" = { workspace =
 * "<member-path>" }} instead of being emitted as an external coordinate.
 *
 * <p>Maven members register their {@code groupId:artifactId}; a sibling dep coordinate
 * ({@code groupId:artifactId}, the version stripped) is looked up here. Gradle members register the
 * Gradle project path derived from their directory (e.g. {@code lib} for {@code project(":lib")}); a
 * {@code project(...)} notation is normalized to that path and looked up here, while the dependency
 * key comes from the target member's emitted {@code group:name}.
 */
final class WorkspaceMemberRegistry {
    private final Map<String, Member> byKey = new HashMap<>();

    void register(String key, String memberPath) {
        register(key, memberPath, key);
    }

    void register(String key, String memberPath, String coordinate) {
        if (key != null && !key.isBlank() && memberPath != null && !memberPath.isBlank()) {
            byKey.put(key, new Member(memberPath, coordinate == null || coordinate.isBlank() ? key : coordinate));
        }
    }

    /** The member path a key resolves to, or {@code null} when the key is not a workspace member. */
    String pathFor(String key) {
        Member member = memberFor(key);
        return member == null ? null : member.path();
    }

    /** The member facts a key resolves to, or {@code null} when the key is not a workspace member. */
    Member memberFor(String key) {
        return key == null ? null : byKey.get(key);
    }

    record Member(String path, String coordinate) {
    }
}
