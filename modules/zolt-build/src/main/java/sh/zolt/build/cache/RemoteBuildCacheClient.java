package sh.zolt.build.cache;

import sh.zolt.maven.repository.RepositoryAuthentication;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * A dumb-HTTP client for a remote build cache: GET to read a blob, PUT to write one. Compatible with
 * Artifactory/Nexus generic repositories. Reads and writes go through the shared {@link HttpClient}
 * (proxy and CA trust), and auth is a single {@code Authorization} header (Basic or Bearer) reusing the
 * repository authentication model. Nothing here ever throws into a build: reads degrade to a miss and
 * writes report an outcome the caller turns into a warning.
 */
public final class RemoteBuildCacheClient {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final URI baseUri;
    private final Optional<RepositoryAuthentication> authentication;
    private final boolean push;
    private final Duration timeout;

    public RemoteBuildCacheClient(
            HttpClient httpClient,
            URI baseUri,
            Optional<RepositoryAuthentication> authentication,
            boolean push) {
        this(httpClient, baseUri, authentication, push, DEFAULT_TIMEOUT);
    }

    RemoteBuildCacheClient(
            HttpClient httpClient,
            URI baseUri,
            Optional<RepositoryAuthentication> authentication,
            boolean push,
            Duration timeout) {
        this.httpClient = httpClient;
        this.baseUri = normalizeBase(baseUri);
        this.authentication = authentication == null ? Optional.empty() : authentication;
        this.push = push;
        this.timeout = timeout;
    }

    public boolean push() {
        return push;
    }

    /** GET the entry at {@code path}; returns its bytes on 200, empty on 404 or any error (a miss). */
    Optional<byte[]> get(String path) {
        HttpRequest request = authorized(HttpRequest.newBuilder(resolve(path)).timeout(timeout).GET()).build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return Optional.of(response.body());
            }
            return Optional.empty();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    /** PUT the file at {@code path}; a 2xx is success, 401/403 is unauthorized, anything else fails. */
    RemoteUploadOutcome put(String path, Path file) {
        try {
            HttpRequest request = authorized(HttpRequest.newBuilder(resolve(path))
                    .timeout(timeout)
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofFile(file)))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return RemoteUploadOutcome.UPLOADED;
            }
            if (status == 401 || status == 403) {
                return RemoteUploadOutcome.UNAUTHORIZED;
            }
            return RemoteUploadOutcome.FAILED;
        } catch (IOException exception) {
            return RemoteUploadOutcome.FAILED;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return RemoteUploadOutcome.FAILED;
        }
    }

    URI baseUri() {
        return baseUri;
    }

    private HttpRequest.Builder authorized(HttpRequest.Builder builder) {
        authentication.ifPresent(value -> builder.header("Authorization", value.authorizationHeaderValue()));
        return builder;
    }

    private URI resolve(String path) {
        return URI.create(baseUri + path);
    }

    private static URI normalizeBase(URI baseUri) {
        String text = baseUri.toString();
        return text.endsWith("/") ? baseUri : URI.create(text + "/");
    }
}
