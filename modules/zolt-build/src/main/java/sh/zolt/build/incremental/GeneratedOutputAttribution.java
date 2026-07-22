package sh.zolt.build.incremental;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The annotation-processor attribution returned by a javac worker compile: for each generated output,
 * the originating top-level types the processor declared for it. {@code present} is false when the
 * compile did not run through the attributing worker path at all (so nothing was captured), and
 * {@code unattributed} is true when the worker saw a generated output it could not fully explain. Any
 * doubt here must force a full recompile — the attribution is only trusted when present and attributed.
 */
public record GeneratedOutputAttribution(boolean present, boolean unattributed, List<Entry> entries) {
    public static final int KIND_SOURCE = 0;
    public static final int KIND_CLASS = 1;
    public static final int KIND_RESOURCE = 2;

    public GeneratedOutputAttribution {
        entries = List.copyOf(entries);
    }

    public static GeneratedOutputAttribution absent() {
        return new GeneratedOutputAttribution(false, false, List.of());
    }

    /**
     * Combines this attribution with the attribution of a later compile in the same build (e.g. an
     * ABI-driven dependent wave). Attribution is only complete when every compile captured it, so
     * {@code present} is conjunctive while {@code unattributed} is disjunctive.
     */
    public GeneratedOutputAttribution merge(GeneratedOutputAttribution other) {
        if (other == null) {
            return this;
        }
        Map<Path, Entry> merged = new LinkedHashMap<>();
        entries.forEach(entry -> merged.put(entry.path(), entry));
        other.entries.forEach(entry -> merged.put(entry.path(), entry));
        return new GeneratedOutputAttribution(
                present && other.present,
                unattributed || other.unattributed,
                new ArrayList<>(merged.values()));
    }

    public record Entry(Path path, int kind, String createdType, List<String> originatingTypes) {
        public Entry {
            path = path.toAbsolutePath().normalize();
            createdType = createdType == null ? "" : createdType;
            originatingTypes = List.copyOf(originatingTypes);
        }
    }
}
