package sh.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class RepositoryHttpContractsTest {
    @Test
    void basicAuthenticationHeaderIsDeterministic() {
        RepositoryAuthentication authentication = new RepositoryAuthentication("zolt-user", "zolt-secret");

        assertEquals(
                "Basic " + Base64.getEncoder().encodeToString("zolt-user:zolt-secret".getBytes(StandardCharsets.UTF_8)),
                authentication.authorizationHeaderValue());
        assertTrue(RepositoryAuthentication.none().isEmpty());
    }

    @Test
    void fetchRequestUsesGetTimeoutAndAuthenticationHeader() {
        RepositoryAuthentication authentication = new RepositoryAuthentication("zolt-user", "zolt-secret");
        RepositoryHttpPolicy policy = new RepositoryHttpPolicy(Duration.ofSeconds(7), 1, Duration.ZERO);

        HttpRequest request = RepositoryHttpRequests.fetchRequest(
                URI.create("https://repo.example.test/maven2/com/example/app/1.0.0/app-1.0.0.pom"),
                Optional.of(authentication),
                policy);

        assertEquals("GET", request.method());
        assertEquals(Duration.ofSeconds(7), request.timeout().orElseThrow());
        assertEquals(
                authentication.authorizationHeaderValue(),
                request.headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void uploadRequestUsesPutTimeoutAndAuthenticationHeader() {
        RepositoryAuthentication authentication = new RepositoryAuthentication("zolt-user", "zolt-secret");
        RepositoryHttpPolicy policy = new RepositoryHttpPolicy(Duration.ofSeconds(9), 1, Duration.ZERO);

        HttpRequest request = RepositoryHttpRequests.uploadRequest(
                URI.create("https://repo.example.test/maven2/com/example/app/1.0.0/app-1.0.0.pom"),
                HttpRequest.BodyPublishers.ofString("<project/>"),
                Optional.of(authentication),
                policy);

        assertEquals("PUT", request.method());
        assertEquals(Duration.ofSeconds(9), request.timeout().orElseThrow());
        assertEquals(
                authentication.authorizationHeaderValue(),
                request.headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void authenticationRejectsBlankUsernameAndPassword() {
        IllegalArgumentException usernameException = assertThrows(
                IllegalArgumentException.class,
                () -> new RepositoryAuthentication(" ", "zolt-secret"));
        IllegalArgumentException passwordException = assertThrows(
                IllegalArgumentException.class,
                () -> new RepositoryAuthentication("zolt-user", ""));

        assertEquals("Repository authentication username must be non-empty.", usernameException.getMessage());
        assertEquals("Repository authentication password must be non-empty.", passwordException.getMessage());
    }

    @Test
    void authenticationRejectsNullUsernameAndPassword() {
        IllegalArgumentException usernameException = assertThrows(
                IllegalArgumentException.class,
                () -> new RepositoryAuthentication(null, "zolt-secret"));
        IllegalArgumentException passwordException = assertThrows(
                IllegalArgumentException.class,
                () -> new RepositoryAuthentication("zolt-user", null));

        assertEquals("Repository authentication username must be non-empty.", usernameException.getMessage());
        assertEquals("Repository authentication password must be non-empty.", passwordException.getMessage());
    }

    @Test
    void bearerAuthenticationHeaderUsesTheToken() {
        RepositoryAuthentication authentication = RepositoryAuthentication.bearer("pat-abc123");

        assertEquals("Bearer pat-abc123", authentication.authorizationHeaderValue());
    }

    @Test
    void bearerTokenRequestSendsBearerAuthorizationHeaderOnFetchAndUpload() {
        RepositoryAuthentication authentication = RepositoryAuthentication.bearer("pat-abc123");
        URI uri = URI.create("https://repo.example.test/maven2/com/example/app/1.0.0/app-1.0.0.pom");

        HttpRequest fetch = RepositoryHttpRequests.fetchRequest(
                uri, Optional.of(authentication), RepositoryHttpPolicy.defaults());
        HttpRequest upload = RepositoryHttpRequests.uploadRequest(
                uri, HttpRequest.BodyPublishers.ofString("<project/>"),
                Optional.of(authentication), RepositoryHttpPolicy.defaults());

        assertEquals("Bearer pat-abc123", fetch.headers().firstValue("Authorization").orElseThrow());
        assertEquals("Bearer pat-abc123", upload.headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void bearerRejectsBlankToken() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> RepositoryAuthentication.bearer(" "));

        assertEquals("Repository authentication token must be non-empty.", exception.getMessage());
    }

    @Test
    void repositoryHttpPolicyRejectsInvalidValues() {
        IllegalArgumentException timeoutException = assertThrows(
                IllegalArgumentException.class,
                () -> new RepositoryHttpPolicy(Duration.ZERO, 1, Duration.ZERO));
        IllegalArgumentException attemptsException = assertThrows(
                IllegalArgumentException.class,
                () -> new RepositoryHttpPolicy(Duration.ofSeconds(1), 0, Duration.ZERO));
        IllegalArgumentException backoffException = assertThrows(
                IllegalArgumentException.class,
                () -> new RepositoryHttpPolicy(Duration.ofSeconds(1), 1, Duration.ofMillis(-1)));

        assertEquals("Repository request timeout must be greater than zero.", timeoutException.getMessage());
        assertEquals("Repository max attempts must be at least 1.", attemptsException.getMessage());
        assertEquals("Repository retry backoff must not be negative.", backoffException.getMessage());
    }

    @Test
    void fetchRequestRejectsNonHttpRepositoryUriWithRemediation() {
        RepositoryClientException exception = assertThrows(
                RepositoryClientException.class,
                () -> RepositoryHttpRequests.fetchRequest(
                        URI.create("file:/tmp/repo/example.pom"),
                        RepositoryAuthentication.none(),
                        RepositoryHttpPolicy.defaults()));

        assertTrue(exception.getMessage().contains("Could not download from repository URL file:/tmp/repo/example.pom"));
        assertTrue(exception.getMessage().contains("Check the repository URL and remove embedded credentials."));
    }

    @Test
    void uploadRequestRejectsNonHttpRepositoryUriAndRedactsCredentials() {
        RepositoryClientException exception = assertThrows(
                RepositoryClientException.class,
                () -> RepositoryHttpRequests.uploadRequest(
                        URI.create("ftp://repo-user:super-secret@repo.example.test/maven2/example.pom"),
                        HttpRequest.BodyPublishers.ofString("<project/>"),
                        RepositoryAuthentication.none(),
                        RepositoryHttpPolicy.defaults()));

        assertTrue(exception.getMessage().contains("Could not upload to repository URL"));
        assertTrue(exception.getMessage().contains("ftp://***@repo.example.test/maven2/example.pom"));
        assertFalse(exception.getMessage().contains("repo-user"));
        assertFalse(exception.getMessage().contains("super-secret"));
    }

    @Test
    void diagnosticUriLeavesUrlWithoutUserInfoUnchanged() {
        URI uri = URI.create("https://repo.example.test/maven2/com/example/app/1.0.0/app-1.0.0.pom");

        assertEquals(uri.toString(), RepositoryHttpRequests.diagnosticUri(uri));
    }
}
