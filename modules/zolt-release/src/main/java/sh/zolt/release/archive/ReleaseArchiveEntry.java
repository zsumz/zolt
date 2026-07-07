package sh.zolt.release.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

record ReleaseArchiveEntry(Path source, byte[] content, String name, int mode, boolean directory) {
    static ReleaseArchiveEntry file(Path source, String name, int mode) {
        return new ReleaseArchiveEntry(source, null, name, mode, false);
    }

    static ReleaseArchiveEntry content(byte[] content, String name, int mode) {
        return new ReleaseArchiveEntry(null, content.clone(), name, mode, false);
    }

    static ReleaseArchiveEntry directory(String name) {
        return new ReleaseArchiveEntry(null, null, name, 0755, true);
    }

    long size() throws IOException {
        if (directory) {
            return 0;
        }
        return content == null ? Files.size(source) : content.length;
    }

    void writeTo(OutputStream output) throws IOException {
        if (content == null) {
            Files.copy(source, output);
        } else {
            output.write(content);
        }
    }
}
