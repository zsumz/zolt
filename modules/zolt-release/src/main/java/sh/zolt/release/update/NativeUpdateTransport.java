package sh.zolt.release.update;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

interface NativeUpdateTransport {
    static NativeUpdateTransport standard() {
        return new StandardNativeUpdateTransport();
    }

    byte[] downloadBytes(URI uri, int maxBytes, String description) throws IOException;

    void download(URI uri, Path output) throws IOException;
}
