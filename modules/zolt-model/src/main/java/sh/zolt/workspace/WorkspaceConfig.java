package sh.zolt.workspace;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record WorkspaceConfig(
        String name,
        List<String> members,
        List<String> defaultMembers,
        Map<String, String> repositories,
        Map<String, String> platforms) {
    public WorkspaceConfig {
        members = List.copyOf(members);
        defaultMembers = List.copyOf(defaultMembers);
        repositories = Collections.unmodifiableMap(new LinkedHashMap<>(repositories));
        platforms = Collections.unmodifiableMap(new LinkedHashMap<>(platforms));
    }
}
