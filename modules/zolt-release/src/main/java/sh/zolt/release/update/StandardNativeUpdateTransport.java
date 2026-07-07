package sh.zolt.release.update;

import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class StandardNativeUpdateTransport implements NativeUpdateTransport {
    @Override
    public byte[] downloadBytes(URI uri, int maxBytes, String description) throws IOException {
        try (var input = open(uri)) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw new NativeUpdateException("Downloaded " + description + " is too large.");
            }
            return bytes;
        }
    }

    @Override
    public void download(URI uri, Path output) throws IOException {
        if ("file".equals(uri.getScheme())) {
            Files.copy(Path.of(uri), output, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        try (var input = open(uri)) {
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static java.io.InputStream open(URI uri) throws IOException {
        if ("file".equals(uri.getScheme())) {
            return Files.newInputStream(Path.of(uri));
        }
        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        return connection.getInputStream();
    }
}
