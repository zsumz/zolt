package com.zolt.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class IncrementalCompileStateCodec {
    private static final String VERSION = "1";

    Optional<IncrementalCompileState> read(Path statePath) {
        if (!Files.isRegularFile(statePath)) {
            return Optional.empty();
        }
        try {
            return parse(Files.readString(statePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not read incremental compile state at "
                            + statePath
                            + ". Delete the file or run a full build to refresh it.",
                    exception);
        }
    }

    void write(Path statePath, IncrementalCompileState state) {
        try {
            Files.createDirectories(statePath.getParent());
            Files.writeString(statePath, format(state), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not write incremental compile state at "
                            + statePath
                            + ". Check that the build output directory is writable.",
                    exception);
        }
    }

    String format(IncrementalCompileState state) {
        StringBuilder content = new StringBuilder();
        line(content, "version", VERSION);
        line(content, "scope", state.scope());
        encodedLine(content, "projectDirectory", state.projectDirectory().toString());
        encodedLine(content, "outputDirectory", state.outputDirectory().toString());
        encodedLine(content, "generatedSourcesDirectory", state.generatedSourcesDirectory().toString());
        line(content, "compilerSettingsHash", state.compilerSettingsHash());
        line(content, "buildFingerprintSha256", state.buildFingerprintSha256());
        state.sourceRoots().forEach(root -> encodedRecord(content, "sourceRoot", root));
        state.generatedSourceRoots().forEach(root -> encodedRecord(content, "generatedSourceRoot", root));
        state.compileClasspath().forEach(entry -> encodedRecord(
                content,
                "compileClasspath",
                entry.path().toString(),
                entry.hash()));
        state.processorClasspath().forEach(entry -> encodedRecord(
                content,
                "processorClasspath",
                entry.path().toString(),
                entry.hash()));
        for (IncrementalCompileState.SourceRecord source : state.sources()) {
            encodedRecord(
                    content,
                    "source",
                    source.path().toString(),
                    source.sourceRoot().toString(),
                    source.generatedSourceStepId().orElse(""),
                    source.contentHash(),
                    source.packageName());
            source.declaredTypes().forEach(type -> encodedRecord(content, "sourceDeclaredType", source.path().toString(), type));
            source.classOutputs().forEach(output -> encodedRecord(content, "sourceClass", source.path().toString(), output.toString()));
            source.referencedClasses().forEach(reference -> encodedRecord(content, "sourceReference", source.path().toString(), reference));
        }
        for (IncrementalCompileState.ClassRecord classRecord : state.classes()) {
            encodedRecord(
                    content,
                    "class",
                    classRecord.binaryName(),
                    classRecord.outputPath().toString(),
                    classRecord.classFileHash(),
                    classRecord.abiHash(),
                    classRecord.packagePrivateAbiHash(),
                    Integer.toString(classRecord.accessFlags()),
                    classRecord.superName().orElse(""));
            classRecord.interfaces().forEach(interfaceName -> encodedRecord(
                    content,
                    "classInterface",
                    classRecord.binaryName(),
                    interfaceName));
        }
        state.reverseDependencies().forEach((className, sources) ->
                sources.forEach(source -> encodedRecord(content, "reverseDependency", className, source.toString())));
        return content.toString();
    }

    Optional<IncrementalCompileState> parse(String content) {
        try {
            return parseStrict(content);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<IncrementalCompileState> parseStrict(String content) {
        List<String> lines = content.lines().toList();
        if (lines.size() < 7 || !("version=" + VERSION).equals(lines.getFirst())) {
            return Optional.empty();
        }
        ScalarFields fields = new ScalarFields();
        List<String> sourceRoots = new ArrayList<>();
        List<String> generatedSourceRoots = new ArrayList<>();
        List<IncrementalCompileState.ClasspathEntry> compileClasspath = new ArrayList<>();
        List<IncrementalCompileState.ClasspathEntry> processorClasspath = new ArrayList<>();
        Map<Path, SourceBuilder> sources = new LinkedHashMap<>();
        Map<String, ClassBuilder> classes = new LinkedHashMap<>();
        Map<String, List<Path>> reverseDependencies = new LinkedHashMap<>();

        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("scope=")) {
                fields.scope = line.substring("scope=".length());
            } else if (line.startsWith("projectDirectory=")) {
                fields.projectDirectory = pathValue(line, "projectDirectory=");
            } else if (line.startsWith("outputDirectory=")) {
                fields.outputDirectory = pathValue(line, "outputDirectory=");
            } else if (line.startsWith("generatedSourcesDirectory=")) {
                fields.generatedSourcesDirectory = pathValue(line, "generatedSourcesDirectory=");
            } else if (line.startsWith("compilerSettingsHash=")) {
                fields.compilerSettingsHash = line.substring("compilerSettingsHash=".length());
            } else if (line.startsWith("buildFingerprintSha256=")) {
                fields.buildFingerprintSha256 = line.substring("buildFingerprintSha256=".length());
            } else {
                String[] parts = line.split("\t", -1);
                if (parts.length == 0) {
                    return Optional.empty();
                }
                switch (parts[0]) {
                    case "sourceRoot" -> sourceRoots.add(decodedPart(parts, 1, 2));
                    case "generatedSourceRoot" -> generatedSourceRoots.add(decodedPart(parts, 1, 2));
                    case "compileClasspath" -> compileClasspath.add(new IncrementalCompileState.ClasspathEntry(
                            pathPart(parts, 1, 3),
                            decodedPart(parts, 2, 3)));
                    case "processorClasspath" -> processorClasspath.add(new IncrementalCompileState.ClasspathEntry(
                            pathPart(parts, 1, 3),
                            decodedPart(parts, 2, 3)));
                    case "source" -> source(sources, pathPart(parts, 1, 6))
                            .set(
                                    pathPart(parts, 1, 6),
                                    pathPart(parts, 2, 6),
                                    decodedPart(parts, 3, 6),
                                    decodedPart(parts, 4, 6),
                                    decodedPart(parts, 5, 6));
                    case "sourceDeclaredType" -> source(sources, pathPart(parts, 1, 3))
                            .declaredTypes.add(decodedPart(parts, 2, 3));
                    case "sourceClass" -> source(sources, pathPart(parts, 1, 3))
                            .classOutputs.add(pathPart(parts, 2, 3));
                    case "sourceReference" -> source(sources, pathPart(parts, 1, 3))
                            .referencedClasses.add(decodedPart(parts, 2, 3));
                    case "class" -> classes.computeIfAbsent(decodedPart(parts, 1, 8), ClassBuilder::new)
                            .set(
                                    decodedPart(parts, 1, 8),
                                    pathPart(parts, 2, 8),
                                    decodedPart(parts, 3, 8),
                                    decodedPart(parts, 4, 8),
                                    decodedPart(parts, 5, 8),
                                    Integer.parseInt(decodedPart(parts, 6, 8)),
                                    decodedPart(parts, 7, 8));
                    case "classInterface" -> classes.computeIfAbsent(decodedPart(parts, 1, 3), ClassBuilder::new)
                            .interfaces.add(decodedPart(parts, 2, 3));
                    case "reverseDependency" -> reverseDependencies
                            .computeIfAbsent(decodedPart(parts, 1, 3), ignored -> new ArrayList<>())
                            .add(pathPart(parts, 2, 3));
                    default -> {
                        return Optional.empty();
                    }
                }
            }
        }

        return Optional.of(new IncrementalCompileState(
                fields.scope,
                fields.projectDirectory,
                fields.outputDirectory,
                fields.generatedSourcesDirectory,
                fields.compilerSettingsHash,
                fields.buildFingerprintSha256,
                sourceRoots,
                generatedSourceRoots,
                compileClasspath,
                processorClasspath,
                sources.values().stream().map(SourceBuilder::build).toList(),
                classes.values().stream().map(ClassBuilder::build).toList(),
                reverseDependencies));
    }

    private static SourceBuilder source(Map<Path, SourceBuilder> sources, Path path) {
        return sources.computeIfAbsent(path, SourceBuilder::new);
    }

    private static Path pathValue(String line, String prefix) {
        return Path.of(decode(line.substring(prefix.length()))).toAbsolutePath().normalize();
    }

    private static Path pathPart(String[] parts, int index, int expectedLength) {
        return Path.of(decodedPart(parts, index, expectedLength)).toAbsolutePath().normalize();
    }

    private static String decodedPart(String[] parts, int index, int expectedLength) {
        if (parts.length != expectedLength) {
            throw new IllegalArgumentException("Unexpected incremental compile state field count.");
        }
        return decode(parts[index]);
    }

    private static void line(StringBuilder content, String name, String value) {
        content.append(name).append('=').append(value).append('\n');
    }

    private static void encodedLine(StringBuilder content, String name, String value) {
        content.append(name).append('=').append(encode(value)).append('\n');
    }

    private static void encodedRecord(StringBuilder content, String name, String... values) {
        content.append(name);
        for (String value : values) {
            content.append('\t').append(encode(value));
        }
        content.append('\n');
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static final class ScalarFields {
        private String scope;
        private Path projectDirectory;
        private Path outputDirectory;
        private Path generatedSourcesDirectory;
        private String compilerSettingsHash;
        private String buildFingerprintSha256;
    }

    private static final class SourceBuilder {
        private final Path path;
        private Path sourceRoot;
        private Optional<String> generatedSourceStepId = Optional.empty();
        private String contentHash;
        private String packageName;
        private final List<String> declaredTypes = new ArrayList<>();
        private final List<Path> classOutputs = new ArrayList<>();
        private final List<String> referencedClasses = new ArrayList<>();

        private SourceBuilder(Path path) {
            this.path = path;
        }

        private void set(Path path, Path sourceRoot, String generatedSourceStepId, String contentHash, String packageName) {
            if (!this.path.equals(path)) {
                throw new IllegalArgumentException("Incremental compile source path mismatch.");
            }
            this.sourceRoot = sourceRoot;
            this.generatedSourceStepId = generatedSourceStepId.isBlank()
                    ? Optional.empty()
                    : Optional.of(generatedSourceStepId);
            this.contentHash = contentHash;
            this.packageName = packageName;
        }

        private IncrementalCompileState.SourceRecord build() {
            return new IncrementalCompileState.SourceRecord(
                    path,
                    sourceRoot,
                    generatedSourceStepId,
                    contentHash,
                    packageName,
                    declaredTypes,
                    classOutputs,
                    referencedClasses);
        }
    }

    private static final class ClassBuilder {
        private final String binaryName;
        private Path outputPath;
        private String classFileHash;
        private String abiHash;
        private String packagePrivateAbiHash;
        private int accessFlags;
        private Optional<String> superName = Optional.empty();
        private final List<String> interfaces = new ArrayList<>();

        private ClassBuilder(String binaryName) {
            this.binaryName = binaryName;
        }

        private void set(
                String binaryName,
                Path outputPath,
                String classFileHash,
                String abiHash,
                String packagePrivateAbiHash,
                int accessFlags,
                String superName) {
            if (!this.binaryName.equals(binaryName)) {
                throw new IllegalArgumentException("Incremental compile class name mismatch.");
            }
            this.outputPath = outputPath;
            this.classFileHash = classFileHash;
            this.abiHash = abiHash;
            this.packagePrivateAbiHash = packagePrivateAbiHash;
            this.accessFlags = accessFlags;
            this.superName = superName.isBlank() ? Optional.empty() : Optional.of(superName);
        }

        private IncrementalCompileState.ClassRecord build() {
            return new IncrementalCompileState.ClassRecord(
                    binaryName,
                    outputPath,
                    classFileHash,
                    abiHash,
                    packagePrivateAbiHash,
                    accessFlags,
                    superName,
                    interfaces);
        }
    }
}
