package sh.zolt.build.manifest;

import java.util.Optional;

public record GeneratedManifest(String path, byte[] content, Optional<String> mainClass) {
    public static final String DEFAULT_PATH = "META-INF/MANIFEST.MF";

    public GeneratedManifest {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Manifest path must be a non-empty path.");
        }
        content = content.clone();
        mainClass = mainClass == null ? Optional.empty() : mainClass;
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
