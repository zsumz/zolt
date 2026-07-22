package sh.zolt.project;

import java.util.Optional;

/**
 * References to the environment variables that supply a repository's credentials. A credential is
 * either HTTP Basic ({@code usernameEnv} + {@code passwordEnv}) or a Bearer token
 * ({@code tokenEnv}); the two modes are mutually exclusive. Only variable names are stored, never
 * secret values.
 */
public record RepositoryCredentialSettings(
        String id,
        Optional<String> usernameEnv,
        Optional<String> passwordEnv,
        Optional<String> tokenEnv) {
    public RepositoryCredentialSettings {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Repository credential id must be non-empty.");
        }
        usernameEnv = normalize(usernameEnv);
        passwordEnv = normalize(passwordEnv);
        tokenEnv = normalize(tokenEnv);
        if (tokenEnv.isPresent()) {
            if (usernameEnv.isPresent() || passwordEnv.isPresent()) {
                throw new IllegalArgumentException(
                        "Repository credential `" + id + "` cannot combine tokenEnv with usernameEnv or passwordEnv.");
            }
        } else if (usernameEnv.isEmpty() || passwordEnv.isEmpty()) {
            throw new IllegalArgumentException(
                    "Repository credential `" + id + "` must set either tokenEnv or both usernameEnv and passwordEnv.");
        }
    }

    public static RepositoryCredentialSettings basic(String id, String usernameEnv, String passwordEnv) {
        return new RepositoryCredentialSettings(
                id, Optional.ofNullable(usernameEnv), Optional.ofNullable(passwordEnv), Optional.empty());
    }

    public static RepositoryCredentialSettings token(String id, String tokenEnv) {
        return new RepositoryCredentialSettings(id, Optional.empty(), Optional.empty(), Optional.ofNullable(tokenEnv));
    }

    public boolean usesToken() {
        return tokenEnv.isPresent();
    }

    private static Optional<String> normalize(Optional<String> value) {
        return value == null ? Optional.empty() : value.filter(candidate -> !candidate.isBlank());
    }
}
