package sh.zolt.maven.repository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * An HTTP Authorization value for a repository request, either HTTP Basic (username and password)
 * or a Bearer token (personal access token). The header value is computed once and secret material
 * is never exposed through accessors so it cannot be logged.
 */
public final class RepositoryAuthentication {
    private final String authorizationHeaderValue;

    public RepositoryAuthentication(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Repository authentication username must be non-empty.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Repository authentication password must be non-empty.");
        }
        String credentials = username + ":" + password;
        this.authorizationHeaderValue =
                "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private RepositoryAuthentication(String authorizationHeaderValue) {
        this.authorizationHeaderValue = authorizationHeaderValue;
    }

    public static RepositoryAuthentication bearer(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Repository authentication token must be non-empty.");
        }
        return new RepositoryAuthentication("Bearer " + token);
    }

    public String authorizationHeaderValue() {
        return authorizationHeaderValue;
    }

    public static Optional<RepositoryAuthentication> none() {
        return Optional.empty();
    }
}
