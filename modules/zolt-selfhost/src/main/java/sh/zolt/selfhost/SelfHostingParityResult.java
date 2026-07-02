package sh.zolt.selfhost;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public record SelfHostingParityResult(
        Path bootstrapJar,
        Path zoltJar,
        Set<String> missingFromZolt,
        Set<String> extraInZolt) {
    public SelfHostingParityResult {
        missingFromZolt = Collections.unmodifiableSet(new TreeSet<>(missingFromZolt));
        extraInZolt = Collections.unmodifiableSet(new TreeSet<>(extraInZolt));
    }

    public boolean ok() {
        return missingFromZolt.isEmpty() && extraInZolt.isEmpty();
    }

}
