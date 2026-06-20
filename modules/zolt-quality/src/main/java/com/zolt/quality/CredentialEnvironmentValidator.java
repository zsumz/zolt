package com.zolt.quality;

import com.zolt.project.RepositoryCredentialSettings;
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
        if (isMissingEnvironmentValue(credential.usernameEnv())) {
            missing.add(credential.usernameEnv());
        }
        if (isMissingEnvironmentValue(credential.passwordEnv())) {
            missing.add(credential.passwordEnv());
        }
        return List.copyOf(missing);
    }

    private List<String> placeholderCredentialEnvironmentVariables(RepositoryCredentialSettings credential) {
        List<String> placeholders = new ArrayList<>();
        if (isPlaceholderCredential(environment.apply(credential.usernameEnv()))) {
            placeholders.add(credential.usernameEnv());
        }
        if (isPlaceholderCredential(environment.apply(credential.passwordEnv()))) {
            placeholders.add(credential.passwordEnv());
        }
        return List.copyOf(placeholders);
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
