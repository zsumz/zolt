package sh.zolt.publish;

import java.nio.file.Path;
import java.util.Optional;

public record PublishArtifactPlan(
        String id,
        Optional<String> classifier,
        Path path,
        String sha256,
        String uploadPath) {
    public PublishArtifactPlan {
        if (id == null || id.isBlank()) {
            throw new PublishException("Publish artifact id is required.");
        }
        classifier = classifier == null ? Optional.empty() : classifier;
        path = path == null ? Path.of("") : path;
        sha256 = sha256 == null ? "" : sha256;
        uploadPath = uploadPath == null ? "" : uploadPath;
    }
}
