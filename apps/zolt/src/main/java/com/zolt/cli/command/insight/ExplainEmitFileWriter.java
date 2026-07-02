package com.zolt.cli.command.insight;

import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.command.CommandFailures;
import com.zolt.explain.emit.DraftZoltTomlDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import picocli.CommandLine.Model.CommandSpec;

final class ExplainEmitFileWriter {
    private final CommandSpec spec;
    private final Path outputDirectory;
    private final boolean overwrite;

    ExplainEmitFileWriter(CommandSpec spec, Path outputDirectory, boolean overwrite) {
        this.spec = spec;
        this.outputDirectory = outputDirectory;
        this.overwrite = overwrite;
    }

    void write(Path projectRoot, List<DraftZoltTomlDocument> documents) {
        Path outputRoot = outputDirectory.isAbsolute()
                ? outputDirectory.toAbsolutePath().normalize()
                : projectRoot.resolve(outputDirectory).toAbsolutePath().normalize();
        List<EmitWrite> writes = planWrites(outputRoot, documents);
        if (!overwrite) {
            refuseExistingFiles(writes);
        }
        writeFiles(writes);
        printSummary(writes);
    }

    private void refuseExistingFiles(List<EmitWrite> writes) {
        for (EmitWrite write : writes) {
            if (Files.exists(write.path())) {
                throw CommandFailures.user(
                        spec,
                        "Refusing to overwrite zolt.toml at " + write.path()
                                + ". Use --emit-toml-overwrite to replace existing emitted TOML files.",
                        new IOException("Refusing to overwrite " + write.path()));
            }
        }
    }

    private void writeFiles(List<EmitWrite> writes) {
        for (EmitWrite write : writes) {
            try {
                Files.createDirectories(write.path().getParent());
                if (overwrite) {
                    Files.writeString(
                            write.path(),
                            write.contents(),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE);
                } else {
                    Files.writeString(
                            write.path(),
                            write.contents(),
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                }
            } catch (IOException exception) {
                throw CommandFailures.user(
                        spec,
                        "Could not write emitted zolt.toml at " + write.path()
                                + ". Check that the output directory is writable and rerun.",
                        exception);
            }
        }
    }

    private List<EmitWrite> planWrites(Path outputRoot, List<DraftZoltTomlDocument> documents) {
        List<EmitWrite> writes = new ArrayList<>();
        Set<Path> seen = new LinkedHashSet<>();
        for (DraftZoltTomlDocument document : documents) {
            Path relativePath = Path.of(document.relativePath()).normalize();
            Path target = outputRoot.resolve(relativePath).normalize();
            if (relativePath.isAbsolute() || !target.startsWith(outputRoot)) {
                throw CommandFailures.user(
                        spec,
                        "Refusing to write emitted zolt.toml outside output directory: "
                                + document.relativePath()
                                + ". Choose an output directory and member paths that stay under the workspace root.",
                        new IllegalArgumentException("Emitted path escapes output directory: " + document.relativePath()));
            }
            if (!seen.add(target)) {
                throw CommandFailures.user(
                        spec,
                        "Refusing to write duplicate emitted zolt.toml at " + target
                                + ". Check the migrated workspace member paths and rerun.",
                        new IllegalArgumentException("Duplicate emitted path: " + target));
            }
            writes.add(new EmitWrite(target, document.contents()));
        }
        return List.copyOf(writes);
    }

    private void printSummary(List<EmitWrite> writes) {
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        output.summary(
                writes.size() == 1 ? "Wrote draft zolt.toml" : "Wrote draft Zolt workspace",
                writes.size() + (writes.size() == 1 ? " file" : " files"));
        for (EmitWrite write : writes) {
            output.pointer("wrote", write.path().toString());
        }
    }

    private record EmitWrite(Path path, String contents) {
    }
}
