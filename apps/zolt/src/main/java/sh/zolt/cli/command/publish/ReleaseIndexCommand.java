package sh.zolt.cli.command.publish;

import sh.zolt.cli.CommandHumanOutput;
import sh.zolt.cli.command.CommandFailures;
import sh.zolt.release.channel.ReleaseChannelManifest;
import sh.zolt.release.channel.ReleaseChannelManifestException;
import sh.zolt.release.channel.ReleaseChannelManifestValidator;
import sh.zolt.release.channel.ReleaseIndexManifest;
import sh.zolt.release.channel.ReleaseIndexManifestService;
import sh.zolt.release.channel.ReleaseIndexManifestValidator;
import sh.zolt.release.channel.ReleaseIndexManifestWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "release-index",
        description = "Merge release channel manifests into a release index.",
        hidden = true)
public final class ReleaseIndexCommand implements Callable<Integer> {
    private final ReleaseChannelManifestValidator channelValidator;
    private final ReleaseIndexManifestValidator indexValidator;
    private final ReleaseIndexManifestService indexService;
    private final ReleaseIndexManifestWriter indexWriter;

    @Option(names = "--channel-manifest", required = true, description = "New channel manifest to prepend to the release index.")
    private Path channelManifest;

    @Option(names = "--previous", description = "Previous release index to merge when present.")
    private Path previousIndex;

    @Option(names = "--output", required = true, description = "Release index output path.")
    private Path output;

    @Option(names = "--limit", description = "Maximum release versions to keep in the index.")
    private int limit = 200;

    @Spec
    private CommandSpec spec;

    public ReleaseIndexCommand() {
        this(
                new ReleaseChannelManifestValidator(),
                new ReleaseIndexManifestValidator(),
                new ReleaseIndexManifestService(),
                new ReleaseIndexManifestWriter());
    }

    ReleaseIndexCommand(
            ReleaseChannelManifestValidator channelValidator,
            ReleaseIndexManifestValidator indexValidator,
            ReleaseIndexManifestService indexService,
            ReleaseIndexManifestWriter indexWriter) {
        this.channelValidator = channelValidator;
        this.indexValidator = indexValidator;
        this.indexService = indexService;
        this.indexWriter = indexWriter;
    }

    @Override
    public Integer call() {
        try {
            ReleaseChannelManifest channel = channelValidator.validate(Files.readString(channelManifest));
            ReleaseIndexManifest index = indexService.merge(channel, readPrevious(), limit);
            write(index);

            CommandHumanOutput output = CommandHumanOutput.of(spec);
            output.summary(
                    "Wrote " + index.channel() + " release index",
                    index.versions().size() + " versions",
                    "latest " + index.versions().getFirst().version());
            output.pointer("wrote", this.output.toString());
            return 0;
        } catch (IOException | ReleaseChannelManifestException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private Optional<ReleaseIndexManifest> readPrevious() throws IOException {
        if (previousIndex == null || !Files.isRegularFile(previousIndex)) {
            return Optional.empty();
        }
        return Optional.of(indexValidator.validate(Files.readString(previousIndex)));
    }

    private void write(ReleaseIndexManifest index) throws IOException {
        Path normalizedOutput = output.toAbsolutePath().normalize();
        if (normalizedOutput.getParent() != null) {
            Files.createDirectories(normalizedOutput.getParent());
        }
        Files.writeString(normalizedOutput, indexWriter.write(index), StandardCharsets.UTF_8);
    }
}
