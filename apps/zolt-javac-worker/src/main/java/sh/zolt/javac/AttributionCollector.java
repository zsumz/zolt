package sh.zolt.javac;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thread-safe sink that reconciles two independent views of what an annotation-processor round wrote:
 * the {@link javax.annotation.processing.Filer} calls (with their originating elements) and the
 * ground-truth files the {@link OutputTrackingFileManager} actually saw created under the source- and
 * class-output locations. Generated sources and generated resources are each reconciled against the
 * matching Filer records: any output the file manager observed that no Filer call explains, or any
 * Filer output produced without an originating element, marks the whole result unattributed.
 */
final class AttributionCollector {
    private final Map<Path, GeneratedFileRecord> entries = new LinkedHashMap<>();
    private final Set<Path> filerSourceOutputs = new LinkedHashSet<>();
    private final Set<Path> filerResourceOutputs = new LinkedHashSet<>();
    private final Set<Path> observedSourceOutputs = new LinkedHashSet<>();
    private final Set<Path> observedResourceOutputs = new LinkedHashSet<>();
    private boolean unattributedOrigin;

    synchronized void recordSource(Path path, String createdType, List<String> originating) {
        filerSourceOutputs.add(path);
        record(path, GeneratedFileRecord.KIND_SOURCE, createdType, originating);
    }

    synchronized void recordClass(Path path, String createdType, List<String> originating) {
        record(path, GeneratedFileRecord.KIND_CLASS, createdType, originating);
    }

    synchronized void recordResource(Path path, List<String> originating) {
        filerResourceOutputs.add(path);
        record(path, GeneratedFileRecord.KIND_RESOURCE, "", originating);
    }

    synchronized void observeSourceOutput(Path path) {
        observedSourceOutputs.add(path.toAbsolutePath().normalize());
    }

    synchronized void observeResourceOutput(Path path) {
        observedResourceOutputs.add(path.toAbsolutePath().normalize());
    }

    synchronized AttributionCompileResult result(int exitCode, String diagnostics) {
        boolean unattributed = unattributedOrigin
                || hasUnexplained(observedSourceOutputs, filerSourceOutputs)
                || hasUnexplained(observedResourceOutputs, filerResourceOutputs);
        return new AttributionCompileResult(
                exitCode,
                diagnostics,
                true,
                unattributed,
                new ArrayList<>(entries.values()));
    }

    private void record(Path path, int kind, String createdType, List<String> originating) {
        if (originating.isEmpty()) {
            unattributedOrigin = true;
        }
        entries.put(path, new GeneratedFileRecord(path.toString(), kind, createdType, originating));
    }

    private static boolean hasUnexplained(Set<Path> observed, Set<Path> explained) {
        for (Path path : observed) {
            if (!explained.contains(path)) {
                return true;
            }
        }
        return false;
    }
}
