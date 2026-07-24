package sh.zolt.publish;

import sh.zolt.net.NetworkTransport;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Talks to the Sonatype Central Portal Publisher API
 * (<a href="https://central.sonatype.org/publish/publish-portal-api/">documented contract</a>):
 * {@code POST /api/v1/publisher/upload} (multipart bundle) and {@code POST /api/v1/publisher/status}.
 * Authentication is {@code Authorization: Bearer <token>}, where the token is the base64
 * {@code user:password} Portal user token. All HTTP goes through the shared {@link NetworkTransport}.
 */
public final class CentralPortalClient {
    private static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration STATUS_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;

    public CentralPortalClient() {
        this(NetworkTransport.fromEnvironment());
    }

    public CentralPortalClient(NetworkTransport transport) {
        this.httpClient = transport.newHttpClient();
    }

    /** Uploads {@code bundle} and returns the deployment id assigned by the Portal. */
    public String upload(
            String baseUrl,
            Path bundle,
            String token,
            CentralPublishingType publishingType,
            Optional<String> deploymentName) {
        String boundary = "ZoltCentralBundle" + UUID.randomUUID();
        HttpRequest.BodyPublisher body = multipartBody(bundle, boundary);
        StringBuilder url = new StringBuilder(normalizeBase(baseUrl))
                .append("/api/v1/publisher/upload?publishingType=")
                .append(publishingType.apiValue());
        deploymentName.ifPresent(name -> url.append("&name=").append(encode(name)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(UPLOAD_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(body)
                .build();
        HttpResponse<String> response = send(request, "upload the Central bundle");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new PublishException(
                    "Central Portal rejected the upload (HTTP " + response.statusCode() + "). "
                            + "Next: check the [publish.central].tokenEnv credentials and the bundle contents.\n"
                            + response.body().stripTrailing());
        }
        String deploymentId = response.body().strip();
        if (deploymentId.isBlank()) {
            throw new PublishException("Central Portal accepted the upload but returned no deployment id.");
        }
        return deploymentId;
    }

    /** Queries the status of a deployment. */
    public CentralDeploymentStatus status(String baseUrl, String deploymentId, String token) {
        URI uri = URI.create(normalizeBase(baseUrl) + "/api/v1/publisher/status?id=" + encode(deploymentId));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(STATUS_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = send(request, "query the Central deployment status");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new PublishException(
                    "Central Portal returned HTTP " + response.statusCode() + " for deployment " + deploymentId
                            + ". Next: verify the deployment id and token.\n" + response.body().stripTrailing());
        }
        return new CentralDeploymentStatus(
                deploymentId,
                extractJsonString(response.body(), "deploymentState"),
                response.body());
    }

    private HttpResponse<String> send(HttpRequest request, String action) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new PublishException(
                    "Could not " + action + " at " + request.uri().getHost()
                            + ". Check your network, proxy, and the Central Portal base URL, then try again.",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PublishException("Central Portal request was interrupted. Try again.", exception);
        }
    }

    /**
     * Streams the multipart body as a small header publisher, the bundle file itself
     * ({@link HttpRequest.BodyPublishers#ofFile}), and a small footer publisher concatenated together,
     * so a multi-member family bundle is never materialised in memory.
     */
    private static HttpRequest.BodyPublisher multipartBody(Path bundle, String boundary) {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"bundle\"; filename=\"" + bundle.getFileName() + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";
        HttpRequest.BodyPublisher filePublisher;
        try {
            filePublisher = HttpRequest.BodyPublishers.ofFile(bundle);
        } catch (FileNotFoundException exception) {
            throw new PublishException("Could not read Central bundle at " + bundle + ".", exception);
        }
        return HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofString(header, StandardCharsets.UTF_8),
                filePublisher,
                HttpRequest.BodyPublishers.ofString(footer, StandardCharsets.UTF_8));
    }

    private static String normalizeBase(String baseUrl) {
        String trimmed = baseUrl.strip();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String extractJsonString(String json, String field) {
        int keyIndex = json.indexOf("\"" + field + "\"");
        if (keyIndex < 0) {
            return "";
        }
        int colon = json.indexOf(':', keyIndex + field.length() + 2);
        if (colon < 0) {
            return "";
        }
        int quoteStart = json.indexOf('"', colon + 1);
        if (quoteStart < 0) {
            return "";
        }
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) {
            return "";
        }
        return json.substring(quoteStart + 1, quoteEnd);
    }
}
