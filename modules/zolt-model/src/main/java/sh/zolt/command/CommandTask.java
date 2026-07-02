package sh.zolt.command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record CommandTask(
        String name,
        Optional<String> description,
        List<String> cmd,
        Optional<String> cwd,
        Map<String, String> env) {
    public CommandTask {
        description = description == null ? Optional.empty() : description;
        cmd = cmd == null || cmd.isEmpty() ? List.of() : List.copyOf(cmd);
        cwd = cwd == null ? Optional.empty() : cwd;
        env = env == null || env.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(env));
    }
}
