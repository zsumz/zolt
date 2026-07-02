package sh.zolt.build.compile;

import java.nio.file.Path;

public record JavacResult(
        int sourceCount,
        Path outputDirectory,
        String output) {
}
