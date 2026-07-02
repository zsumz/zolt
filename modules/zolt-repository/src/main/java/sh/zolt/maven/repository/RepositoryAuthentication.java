package sh.zolt.maven.repository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

public record RepositoryAuthentication(String username, String password) {
    public RepositoryAuthentication {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Repository authentication username must be non-empty.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Repository authentication password must be non-empty.");
        }
    }

    public String basicAuthorizationHeader() {
        String value = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<RepositoryAuthentication> none() {
        return Optional.empty();
    }
}
