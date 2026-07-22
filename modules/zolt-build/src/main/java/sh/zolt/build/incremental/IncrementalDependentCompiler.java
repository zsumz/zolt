package sh.zolt.build.incremental;

import java.nio.file.Path;
import java.util.List;

@FunctionalInterface
public interface IncrementalDependentCompiler {
    Outcome compile(List<Path> sources);

    record Outcome(int sourceCount, String output) {
        public Outcome {
            output = output == null ? "" : output;
        }
    }
}
