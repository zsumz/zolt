package sh.zolt.project;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The per-step configuration of a {@code kind = "exec"} generated source. {@code tool} is folded in
 * from the referenced {@code [generated.execTools.<name>]} table (mirroring how the shared OpenAPI
 * tool is folded into each OpenAPI step), while {@code toolName} retains the reference for identity,
 * plan output, and round-trip writing.
 */
public record ExecGenerationSettings(
        String toolName,
        ExecToolSettings tool,
        List<String> args,
        ProducesLane produces,
        Optional<String> into,
        Map<String, String> env,
        String cache) {
    public ExecGenerationSettings {
        toolName = toolName == null ? "" : toolName;
        tool = tool == null ? ExecToolSettings.empty() : tool;
        args = args == null ? List.of() : List.copyOf(args);
        into = into == null ? Optional.empty() : into;
        env = sortedCopy(env);
        cache = cache == null || cache.isBlank() ? "content" : cache;
    }

    public static ExecGenerationSettings empty() {
        return new ExecGenerationSettings("", ExecToolSettings.empty(), List.of(), null, Optional.empty(), Map.of(), "content");
    }

    private static Map<String, String> sortedCopy(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }
}
