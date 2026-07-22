package sh.zolt.javac;

import java.util.List;

/**
 * One annotation-processor output observed through the recording {@link javax.annotation.processing.Filer},
 * mapped to the binary names of the top-level types that were declared as its originating elements.
 * {@code createdType} is the principal type declared by a generated source/class file, or empty for a
 * generated resource. An empty {@code originatingTypes} list marks an output the processor produced
 * without attribution, which forces the caller onto the full-recompile path.
 */
record GeneratedFileRecord(String path, int kind, String createdType, List<String> originatingTypes) {
    static final int KIND_SOURCE = 0;
    static final int KIND_CLASS = 1;
    static final int KIND_RESOURCE = 2;

    GeneratedFileRecord {
        originatingTypes = List.copyOf(originatingTypes);
    }
}
