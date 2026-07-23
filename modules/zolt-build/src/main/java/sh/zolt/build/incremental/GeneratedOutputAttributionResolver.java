package sh.zolt.build.incremental;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the raw per-file attribution captured by the worker into per-handwritten-source generated
 * outputs. Each generated file names the originating top-level types the processor declared for it;
 * an originating type is either a handwritten source (resolved directly) or another generated type
 * (resolved transitively until a handwritten root is reached). Attribution is only usable when every
 * generated output — source, class, or resource — resolves to exactly one handwritten root; zero,
 * many, cyclic, or unresolvable roots mark the resolution incomplete so the caller falls back to a
 * full recompile. Resources are attributed by their single root just like sources; only their
 * originating types drive resolution (a resource declares no created type).
 */
final class GeneratedOutputAttributionResolver {
    Resolution resolve(
            List<GeneratedOutputAttribution.Entry> entries,
            Map<String, Path> handwrittenTypeToSource) {
        Map<String, GeneratedOutputAttribution.Entry> createdTypeToEntry = new LinkedHashMap<>();
        for (GeneratedOutputAttribution.Entry entry : entries) {
            if (!entry.createdType().isBlank()) {
                createdTypeToEntry.put(entry.createdType(), entry);
            }
        }
        Map<Path, SourceGenerated> bySource = new LinkedHashMap<>();
        boolean complete = true;
        for (GeneratedOutputAttribution.Entry entry : entries) {
            Path root = singleRoot(entry, handwrittenTypeToSource, createdTypeToEntry);
            if (root == null) {
                complete = false;
                continue;
            }
            SourceGenerated generated = bySource.computeIfAbsent(root, ignored -> new SourceGenerated());
            if (entry.kind() == GeneratedOutputAttribution.KIND_SOURCE) {
                generated.generatedSourceFiles.add(entry.path());
            } else if (entry.kind() == GeneratedOutputAttribution.KIND_RESOURCE) {
                generated.generatedResourceFiles.add(entry.path());
            }
            if (!entry.createdType().isBlank()) {
                generated.generatedTypes.add(entry.createdType());
            }
        }
        return new Resolution(bySource, complete);
    }

    private static Path singleRoot(
            GeneratedOutputAttribution.Entry entry,
            Map<String, Path> handwrittenTypeToSource,
            Map<String, GeneratedOutputAttribution.Entry> createdTypeToEntry) {
        Set<Path> roots = new LinkedHashSet<>();
        if (!collectRoots(entry, handwrittenTypeToSource, createdTypeToEntry, roots, new LinkedHashSet<>())) {
            return null;
        }
        return roots.size() == 1 ? roots.iterator().next() : null;
    }

    private static boolean collectRoots(
            GeneratedOutputAttribution.Entry entry,
            Map<String, Path> handwrittenTypeToSource,
            Map<String, GeneratedOutputAttribution.Entry> createdTypeToEntry,
            Set<Path> roots,
            Set<String> visiting) {
        if (entry.originatingTypes().isEmpty()) {
            return false;
        }
        for (String origin : entry.originatingTypes()) {
            Path handwritten = handwrittenTypeToSource.get(origin);
            if (handwritten != null) {
                roots.add(handwritten);
                continue;
            }
            GeneratedOutputAttribution.Entry parent = createdTypeToEntry.get(origin);
            if (parent == null || !visiting.add(origin)) {
                return false;
            }
            boolean resolved = collectRoots(parent, handwrittenTypeToSource, createdTypeToEntry, roots, visiting);
            visiting.remove(origin);
            if (!resolved) {
                return false;
            }
        }
        return true;
    }

    record Resolution(Map<Path, SourceGenerated> bySource, boolean complete) {
    }

    static final class SourceGenerated {
        private final Set<Path> generatedSourceFiles = new LinkedHashSet<>();
        private final Set<Path> generatedResourceFiles = new LinkedHashSet<>();
        private final Set<String> generatedTypes = new LinkedHashSet<>();

        Set<Path> generatedSourceFiles() {
            return generatedSourceFiles;
        }

        Set<Path> generatedResourceFiles() {
            return generatedResourceFiles;
        }

        Set<String> generatedTypes() {
            return generatedTypes;
        }
    }
}
