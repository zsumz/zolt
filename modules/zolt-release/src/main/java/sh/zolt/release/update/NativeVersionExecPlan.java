package sh.zolt.release.update;

import java.nio.file.Path;
import java.util.List;

public record NativeVersionExecPlan(
        String version,
        Path executable,
        List<String> arguments) {
    public NativeVersionExecPlan {
        arguments = List.copyOf(arguments);
    }
}
