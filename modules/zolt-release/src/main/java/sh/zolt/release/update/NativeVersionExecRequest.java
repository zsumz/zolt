package sh.zolt.release.update;

import java.nio.file.Path;
import java.util.List;

public record NativeVersionExecRequest(
        Path installRoot,
        Path currentExecutable,
        String version,
        List<String> arguments) {
    public NativeVersionExecRequest {
        arguments = List.copyOf(arguments);
    }
}
