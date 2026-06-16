package com.zolt.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.zolt.project.RepositoryCredentialSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CredentialEnvironmentValidatorTest {
    @Test
    void reportsMissingCredentialVariablesInCredentialOrder() {
        CredentialEnvironmentValidator validator = new CredentialEnvironmentValidator(Map.<String, String>of(
                        "USERNAME", "ci-user")
                ::get);

        CredentialEnvironmentCheck check = validator.check(credential());

        assertEquals(List.of("PASSWORD"), check.missing());
        assertEquals(List.of(), check.placeholders());
    }

    @Test
    void reportsPlaceholderCredentialVariablesWithoutValues() {
        CredentialEnvironmentValidator validator = new CredentialEnvironmentValidator(Map.of(
                        "USERNAME", " read.only ",
                        "PASSWORD", "CHANGE-ME")
                ::get);

        CredentialEnvironmentCheck check = validator.check(credential());

        assertEquals(List.of(), check.missing());
        assertEquals(List.of("USERNAME", "PASSWORD"), check.placeholders());
        assertFalse(check.placeholders().contains("read.only"));
        assertFalse(check.placeholders().contains("CHANGE-ME"));
    }

    @Test
    void acceptsRealCredentialValues() {
        CredentialEnvironmentValidator validator = new CredentialEnvironmentValidator(Map.of(
                        "USERNAME", "ci-user",
                        "PASSWORD", "token-123")
                ::get);

        CredentialEnvironmentCheck check = validator.check(credential());

        assertEquals(List.of(), check.missing());
        assertEquals(List.of(), check.placeholders());
    }

    private static RepositoryCredentialSettings credential() {
        return new RepositoryCredentialSettings("company-artifactory", "USERNAME", "PASSWORD");
    }
}
