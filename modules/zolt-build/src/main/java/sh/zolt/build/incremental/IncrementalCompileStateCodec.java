package sh.zolt.build.incremental;

import sh.zolt.build.BuildException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class IncrementalCompileStateCodec {
    private final IncrementalCompileStateFormatter formatter;
    private final IncrementalCompileStateParser parser;

    public IncrementalCompileStateCodec() {
        this(new IncrementalCompileStateFormatter(), new IncrementalCompileStateParser());
    }

    private IncrementalCompileStateCodec(
            IncrementalCompileStateFormatter formatter,
            IncrementalCompileStateParser parser) {
        this.formatter = formatter;
        this.parser = parser;
    }

    public Optional<IncrementalCompileState> read(Path statePath) {
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
        return formatter.format(state);
    }

    Optional<IncrementalCompileState> parse(String content) {
        return parser.parse(content);
    }
}
