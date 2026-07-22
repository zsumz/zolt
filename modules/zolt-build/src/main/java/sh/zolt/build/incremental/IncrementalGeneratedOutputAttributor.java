package sh.zolt.build.incremental;

import sh.zolt.build.incremental.GeneratedOutputAttributionResolver.Resolution;
import sh.zolt.build.incremental.GeneratedOutputAttributionResolver.SourceGenerated;
import sh.zolt.build.incremental.IncrementalCompileState.ClassRecord;
import sh.zolt.build.incremental.IncrementalCompileState.SourceRecord;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Folds captured processor attribution into the source records. For every source recompiled in this
 * build the freshly resolved generated sources and classes are attached; for sources that were not
 * recompiled the previous state's attribution is carried forward unchanged (their generated outputs
 * were left untouched on disk). Generated classes are found from the resolved generated types by
 * matching the compiled class records' top-level binary names.
 */
final class IncrementalGeneratedOutputAttributor {
    private final GeneratedOutputAttributionResolver resolver = new GeneratedOutputAttributionResolver();

    Result apply(
            List<SourceRecord> baseRecords,
            List<ClassRecord> classRecords,
            GeneratedOutputAttribution attribution,
            List<Path> compiledSources,
            Map<Path, SourceRecord> previousSources) {
        Resolution resolution = resolver.resolve(attribution.entries(), handwrittenTypeToSource(baseRecords));
        Map<String, List<Path>> classesByTopLevel = classesByTopLevel(classRecords);
        Set<Path> compiled = new LinkedHashSet<>();
        compiledSources.forEach(path -> compiled.add(path.toAbsolutePath().normalize()));
        List<SourceRecord> records = new ArrayList<>();
        for (SourceRecord base : baseRecords) {
            if (compiled.contains(base.path())) {
                records.add(fresh(base, resolution.bySource().get(base.path()), classesByTopLevel));
            } else {
                records.add(carriedForward(base, previousSources.get(base.path())));
            }
        }
        return new Result(records, resolution.complete());
    }

    private static SourceRecord fresh(
            SourceRecord base,
            SourceGenerated generated,
            Map<String, List<Path>> classesByTopLevel) {
        if (generated == null) {
            return withGenerated(base, List.of(), List.of());
        }
        Set<Path> classes = new LinkedHashSet<>();
        for (String type : generated.generatedTypes()) {
            classes.addAll(classesByTopLevel.getOrDefault(type, List.of()));
        }
        return withGenerated(base, new ArrayList<>(generated.generatedSourceFiles()), new ArrayList<>(classes));
    }

    private static SourceRecord carriedForward(SourceRecord base, SourceRecord previous) {
        if (previous == null) {
            return base;
        }
        return withGenerated(base, previous.generatedSources(), previous.generatedClasses());
    }

    private static SourceRecord withGenerated(
            SourceRecord base,
            List<Path> generatedSources,
            List<Path> generatedClasses) {
        return new SourceRecord(
                base.path(),
                base.sourceRoot(),
                base.generatedSourceStepId(),
                base.contentHash(),
                base.packageName(),
                base.declaredTypes(),
                base.classOutputs(),
                base.referencedClasses(),
                generatedSources,
                generatedClasses);
    }

    private static Map<String, Path> handwrittenTypeToSource(List<SourceRecord> baseRecords) {
        Map<String, Path> typeToSource = new LinkedHashMap<>();
        for (SourceRecord record : baseRecords) {
            for (String declaredType : record.declaredTypes()) {
                typeToSource.putIfAbsent(declaredType, record.path());
            }
        }
        return typeToSource;
    }

    private static Map<String, List<Path>> classesByTopLevel(List<ClassRecord> classRecords) {
        Map<String, List<Path>> classes = new LinkedHashMap<>();
        for (ClassRecord record : classRecords) {
            classes.computeIfAbsent(topLevel(record.binaryName()), ignored -> new ArrayList<>())
                    .add(record.outputPath());
        }
        return classes;
    }

    private static String topLevel(String binaryName) {
        int nested = binaryName.indexOf('$');
        return nested < 0 ? binaryName : binaryName.substring(0, nested);
    }

    record Result(List<SourceRecord> sources, boolean complete) {
    }
}
