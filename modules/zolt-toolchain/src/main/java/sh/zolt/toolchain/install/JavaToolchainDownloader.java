package sh.zolt.toolchain.install;

import sh.zolt.error.ActionableException;
import sh.zolt.toolchain.catalog.JavaToolchainArtifact;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class JavaToolchainDownloader {
    private final HttpClient httpClient;

    public JavaToolchainDownloader() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    JavaToolchainDownloader(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Path download(JavaToolchainArtifact artifact, Path destination) {
        URI uri = artifact.uri();
        try {
            Files.createDirectories(destination.getParent());
            if ("file".equals(uri.getScheme())) {
                Files.copy(Path.of(uri), destination, StandardCopyOption.REPLACE_EXISTING);
                return destination;
            }
            if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
                throw new ActionableException(
                        "Unsupported Java toolchain artifact URI `" + uri + "`.",
                        "Use a file, https, or http artifact URI.");
            }
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ActionableException(
                        "Could not download Java toolchain artifact from " + uri + ".",
                        "The server returned HTTP " + response.statusCode() + "; check the catalog URL and try again.");
            }
            try (InputStream input = response.body()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return destination;
        } catch (IOException exception) {
            throw new ActionableException(
                    "Could not download Java toolchain artifact from " + uri + ".",
                    "Check your network connection and retry `zolt toolchain sync`.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ActionableException(
                    "Java toolchain download was interrupted.",
                    "Retry `zolt toolchain sync`.");
        }
    }
}
