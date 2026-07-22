package sh.zolt.publish;

import java.nio.file.Path;
import java.util.List;

/** The assembled Central Portal upload bundle: the zip on disk and its sorted entry names. */
public record PublishCentralBundleResult(Path bundlePath, List<String> entries) {
    public PublishCentralBundleResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
