package sh.zolt.publish;

import java.nio.file.Path;

/** A source file and the Maven repository-relative path where its staged bytes will publish. */
public record PublicationSource(String repositoryPath, Path source) {
    public PublicationSource {
        if (repositoryPath == null || repositoryPath.isBlank()) {
            throw new PublishException("A publication repository path is required.");
        }
        if (source == null) {
            throw new PublishException("A publication source file is required for `" + repositoryPath + "`.");
        }
    }
}
