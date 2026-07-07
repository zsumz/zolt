package sh.zolt.toml;

import java.nio.file.Path;

public record ToolchainRequirement(
        Path configPath,
        String zoltVersion) {
}
