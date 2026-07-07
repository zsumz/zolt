package sh.zolt.release.update;

import sh.zolt.release.archive.ReleaseArchiveUnpacker;
import sh.zolt.release.channel.ReleaseChannelArtifact;
import java.io.IOException;
import java.nio.file.Path;

final class ReleaseArchiveSupport {
    private ReleaseArchiveSupport() {
    }

    static void unpack(Path archive, Path destination, ReleaseChannelArtifact artifact) throws IOException {
        ReleaseArchiveUnpacker.unpack(archive, destination, artifact.format(), NativeUpdateException::new);
    }
}
