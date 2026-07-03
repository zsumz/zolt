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
import org.junit.jupiter.api.Test;

final class RepositoryHttpContractsTest {
    @Test
    void basicAuthenticationHeaderIsDeterministic() {
        RepositoryAuthentication authentication = new RepositoryAuthentication("zolt-user", "zolt-secret");

        assertEquals(
                "Basic " + Base64.getEncoder().encodeToString("zolt-user:zolt-secret".getBytes(StandardCharsets.UTF_8)),
                authentication.basicAuthorizationHeader());
        assertTrue(RepositoryAuthentication.none().isEmpty());
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
