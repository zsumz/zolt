package sh.zolt.build.compile;

import sh.zolt.build.incremental.GeneratedOutputAttribution;
import java.nio.file.Path;

public record JavacResult(
        int sourceCount,
        Path outputDirectory,
        String output,
        GeneratedOutputAttribution attribution) {
    public JavacResult(int sourceCount, Path outputDirectory, String output) {
        this(sourceCount, outputDirectory, output, GeneratedOutputAttribution.absent());
    }
}
