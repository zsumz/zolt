package com.zolt.build.incremental;

import com.zolt.build.BuildException;
import com.zolt.build.ClassFileAbi;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class IncrementalCompileSourceRecordBuilder {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "(?m)^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\s*;");
    private static final Pattern TOP_LEVEL_TYPE_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:@[\\w.$]+(?:\\([^\\n]*\\))?\\s*)*(?:public\\s+|protected\\s+|private\\s+|abstract\\s+|final\\s+|sealed\\s+|non-sealed\\s+|static\\s+|strictfp\\s+)*"
                    + "(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");

    List<IncrementalCompileState.SourceRecord> sourceRecords(
            Path projectRoot,
            List<Path> sources,
            List<Path> sourceRoots,
            Map<Path, String> generatedStepIds,
            List<ClassFileAbi> classFiles,
            Function<Path, String> fileHasher) {
        Map<SourceKey, List<ClassFileAbi>> classesBySource = classesBySource(classFiles);
        List<IncrementalCompileState.SourceRecord> records = new ArrayList<>();
        for (Path source : sources.stream().map(path -> path.toAbsolutePath().normalize()).sorted().toList()) {
            SourceMetadata metadata = sourceMetadata(source);
            Path sourceRoot = sourceRootFor(sourceRoots, source).orElse(projectRoot);
            List<ClassFileAbi> ownedClasses = classesBySource.getOrDefault(
                    new SourceKey(metadata.packageName(), source.getFileName().toString()),
                    List.of());
            List<String> references = ownedClasses.stream()
                    .flatMap(abi -> abi.referencedClasses().stream())
                    .distinct()
                    .sorted()
                    .toList();
            records.add(new IncrementalCompileState.SourceRecord(
                    source,
                    sourceRoot,
                    generatedSourceStepId(generatedStepIds, source),
                    fileHasher.apply(source),
                    metadata.packageName(),
                    metadata.declaredTypes(),
                    ownedClasses.stream().map(ClassFileAbi::classFile).toList(),
                    references));
        }
        return records;
    }

    private static Map<SourceKey, List<ClassFileAbi>> classesBySource(List<ClassFileAbi> classes) {
        Map<SourceKey, List<ClassFileAbi>> mapped = new LinkedHashMap<>();
        for (ClassFileAbi abi : classes) {
            abi.sourceFileName().ifPresent(sourceFileName -> mapped
                    .computeIfAbsent(new SourceKey(packageName(abi.binaryName()), sourceFileName), ignored -> new ArrayList<>())
                    .add(abi));
        }
        mapped.values().forEach(values -> values.sort(Comparator.comparing(ClassFileAbi::binaryName)));
        return mapped;
    }

    private static Optional<String> generatedSourceStepId(Map<Path, String> generatedStepIds, Path source) {
        return generatedStepIds.entrySet().stream()
                .filter(entry -> source.startsWith(entry.getKey()))
                .max(Comparator.comparingInt(entry -> entry.getKey().getNameCount()))
                .map(Map.Entry::getValue);
    }

    private static Optional<Path> sourceRootFor(List<Path> sourceRoots, Path source) {
        return sourceRoots.stream()
                .filter(source::startsWith)
                .max(Comparator.comparingInt(Path::getNameCount));
    }

    private static SourceMetadata sourceMetadata(Path source) {
        try {
            String content = Files.readString(source, StandardCharsets.UTF_8);
            String packageName = "";
            Matcher packageMatcher = PACKAGE_PATTERN.matcher(content);
            if (packageMatcher.find()) {
                packageName = packageMatcher.group(1);
            }
            Matcher typeMatcher = TOP_LEVEL_TYPE_PATTERN.matcher(content);
            Set<String> declaredTypes = new LinkedHashSet<>();
            while (typeMatcher.find()) {
                declaredTypes.add(packageName.isBlank()
                        ? typeMatcher.group(1)
                        : packageName + "." + typeMatcher.group(1));
            }
            return new SourceMetadata(packageName, declaredTypes.stream().sorted().toList());
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not read source metadata from "
                            + source
                            + ". Check that the source file is readable.",
                    exception);
        }
    }

    private static String packageName(String binaryName) {
        int index = binaryName.lastIndexOf('.');
        return index < 0 ? "" : binaryName.substring(0, index);
    }

    private record SourceKey(String packageName, String sourceFileName) {
    }

    private record SourceMetadata(String packageName, List<String> declaredTypes) {
    }
}
