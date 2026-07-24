package sh.zolt.build.cache;

import sh.zolt.maven.repository.RepositoryAuthentication;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
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
    private static final int BUFFER_SIZE = 8192;

    /** Result of a bounded download: a served body, a miss (404/error), or a body that broke the size cap. */
    enum DownloadOutcome {
        HIT,
        MISS,
        TOO_LARGE
    }

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

    /**
     * Stream the entry at {@code path} into {@code destination}, refusing a body larger than
     * {@code maxBytes} so a hostile or broken remote can never make the client allocate or persist an
     * unbounded blob. Reading stops and the connection is dropped the moment the cap is exceeded. The
     * caller owns {@code destination} and must delete it on any non-{@link DownloadOutcome#HIT HIT}
     * outcome; on a breach the file holds only the truncated prefix that was read before the abort.
     */
    DownloadOutcome download(String path, Path destination, long maxBytes) {
        HttpRequest request = authorized(HttpRequest.newBuilder(resolve(path)).timeout(timeout).GET()).build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                return DownloadOutcome.MISS;
            }
            try (InputStream in = response.body();
                    OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long total = 0L;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > maxBytes) {
                        return DownloadOutcome.TOO_LARGE;
                    }
                    out.write(buffer, 0, read);
                }
            }
            return DownloadOutcome.HIT;
        } catch (IOException exception) {
            return DownloadOutcome.MISS;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return DownloadOutcome.MISS;
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
