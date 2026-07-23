package sh.zolt.publish;

import sh.zolt.project.RepositoryCredentialSettings;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Repository-URL safety and publish-credential preflight checks for the dry-run plan. */
final class PublishRepositoryBlockers {
    private PublishRepositoryBlockers() {
    }

    static boolean hasEmbeddedCredentials(String url) {
        try {
            URI uri = new URI(url);
            return uri.getRawUserInfo() != null && !uri.getRawUserInfo().isBlank();
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    static String redactedRepositoryUrl(String url) {
        try {
            URI uri = new URI(url);
            String userInfo = uri.getRawUserInfo();
            if (userInfo == null || userInfo.isBlank()) {
                return url;
            }
            return url.replace(userInfo + "@", "***@");
        } catch (URISyntaxException exception) {
            return url;
        }
    }

    static List<String> credentialBlockers(
            PublishRepositorySettings repository,
            Map<String, RepositoryCredentialSettings> credentialSettings,
            Function<String, String> environment) {
        if (repository.credentials().isEmpty()) {
            return List.of();
        }
        RepositoryCredentialSettings credential = credentialSettings.get(repository.credentials().orElseThrow());
        if (credential == null) {
            return List.of("missing credential metadata for publish repository `" + repository.id() + "`");
        }
        List<String> missing = new ArrayList<>();
        if (credential.usesToken()) {
            String tokenEnv = credential.tokenEnv().orElseThrow();
            if (missingEnvironment(tokenEnv, environment)) {
                missing.add(tokenEnv);
            }
        } else {
            String usernameEnv = credential.usernameEnv().orElseThrow();
            String passwordEnv = credential.passwordEnv().orElseThrow();
            if (missingEnvironment(usernameEnv, environment)) {
                missing.add(usernameEnv);
            }
            if (missingEnvironment(passwordEnv, environment)) {
                missing.add(passwordEnv);
            }
        }
        if (missing.isEmpty()) {
            return List.of();
        }
        return List.of("missing credential environment "
                + (missing.size() == 1 ? "variable " : "variables ")
                + String.join(", ", missing)
                + " for publish repository `"
                + repository.id()
                + "`");
    }

    private static boolean missingEnvironment(String name, Function<String, String> environment) {
        String value = environment.apply(name);
        return value == null || value.isBlank();
    }
}
