package sh.zolt.publish;

import java.nio.file.Path;

/** Exact immutable-by-convention bytes prepared for one repository path. */
public record StagedPublicationFile(String repositoryPath, Path source, String sha256) {
}
