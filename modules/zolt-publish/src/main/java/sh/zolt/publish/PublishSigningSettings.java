package sh.zolt.publish;

import java.util.Optional;

/**
 * GPG detached-signature configuration from {@code [publish.signing]}. When {@link #enabled()},
 * publish signs every uploaded artifact and the POM with {@code gpg}. {@link #keyId()} selects a
 * signing key ({@code --local-user}); {@link #passphraseEnv()} names an environment variable that
 * holds the passphrase (fed to gpg over stdin, never the command line). When the passphrase env is
 * absent, publish relies on {@code gpg-agent}. Secrets are referenced by environment-variable name
 * only, never by value.
 */
public record PublishSigningSettings(boolean enabled, Optional<String> keyId, Optional<String> passphraseEnv) {
    public PublishSigningSettings {
        keyId = normalize(keyId);
        passphraseEnv = normalize(passphraseEnv);
    }

    public static PublishSigningSettings disabled() {
        return new PublishSigningSettings(false, Optional.empty(), Optional.empty());
    }

    private static Optional<String> normalize(Optional<String> value) {
        return value == null ? Optional.empty() : value.filter(candidate -> !candidate.isBlank()).map(String::trim);
    }
}
