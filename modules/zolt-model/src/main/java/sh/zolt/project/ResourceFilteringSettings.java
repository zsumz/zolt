package sh.zolt.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ResourceFilteringSettings(
        boolean enabled,
        boolean testEnabled,
        List<String> includes,
        ResourceMissingTokenPolicy missing,
        Map<String, ResourceTokenSettings> tokens) {
    public ResourceFilteringSettings {
        includes = includes == null ? List.of() : List.copyOf(includes);
        missing = missing == null ? ResourceMissingTokenPolicy.FAIL : missing;
        tokens = tokens == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(tokens));
    }

    public static ResourceFilteringSettings defaults() {
        return new ResourceFilteringSettings(false, false, List.of(), ResourceMissingTokenPolicy.FAIL, Map.of());
    }
}
