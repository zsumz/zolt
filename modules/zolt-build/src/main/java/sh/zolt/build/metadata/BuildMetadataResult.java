package sh.zolt.build.metadata;

import java.nio.file.Path;
import java.util.List;

public record BuildMetadataResult(List<Path> generatedFiles) {
    public BuildMetadataResult {
        generatedFiles = List.copyOf(generatedFiles);
    }

    public int generatedCount() {
        return generatedFiles.size();
    }
}
