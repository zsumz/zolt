package sh.zolt.quality.execution;

import sh.zolt.project.RepositoryCredentialSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

final class CredentialEnvironmentValidator {
    private final Function<String, String> environment;

    CredentialEnvironmentValidator(Function<String, String> environment) {
        this.environment = environment;
    }

    CredentialEnvironmentCheck check(RepositoryCredentialSettings credential) {
        return new CredentialEnvironmentCheck(
                missingCredentialEnvironmentVariables(credential),
                placeholderCredentialEnvironmentVariables(credential));
    }

    private List<String> missingCredentialEnvironmentVariables(RepositoryCredentialSettings credential) {
        List<String> missing = new ArrayList<>();
        for (String name : environmentNames(credential)) {
            if (isMissingEnvironmentValue(name)) {
                missing.add(name);
            }
        }
        return List.copyOf(missing);
    }

    private List<String> placeholderCredentialEnvironmentVariables(RepositoryCredentialSettings credential) {
        List<String> placeholders = new ArrayList<>();
        for (String name : environmentNames(credential)) {
            if (isPlaceholderCredential(environment.apply(name))) {
                placeholders.add(name);
            }
        }
        return List.copyOf(placeholders);
    }

    private static List<String> environmentNames(RepositoryCredentialSettings credential) {
        if (credential.usesToken()) {
            return List.of(credential.tokenEnv().orElseThrow());
        }
        return List.of(credential.usernameEnv().orElseThrow(), credential.passwordEnv().orElseThrow());
    }

    boolean isMissingEnvironmentValue(String name) {
        String value = environment.apply(name);
        return value == null || value.isBlank();
    }

    private static boolean isPlaceholderCredential(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Set.of(
                "read.only",
                "readonly",
                "change-me",
                "changeme",
                "dummy",
                "example",
                "password",
                "secret").contains(normalized);
    }
}
