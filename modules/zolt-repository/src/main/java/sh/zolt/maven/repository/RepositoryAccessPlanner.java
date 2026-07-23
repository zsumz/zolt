package sh.zolt.maven.repository;

import sh.zolt.project.ProjectConfig;
import sh.zolt.project.RepositoryCredentialSettings;
import sh.zolt.project.RepositorySettings;
import sh.zolt.project.RepositoryUrlPolicy;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Turns {@code [repositories]} configuration into the ordered, authenticated list of repositories to
 * query. Order is stable alphabetical-by-id. Shared verbatim by dependency resolution and by
 * advisory version discovery so both agree on repository order, URL safety, and credential handling.
 */
public final class RepositoryAccessPlanner {
    private final Function<String, String> environment;

    public RepositoryAccessPlanner() {
        this(System::getenv);
    }

    public RepositoryAccessPlanner(Function<String, String> environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public List<RepositoryAccess> plan(ProjectConfig config) {
        List<RepositorySettings> repositories = config.repositorySettings().values().stream()
                .sorted(Comparator.comparing(RepositorySettings::id))
                .toList();
        if (repositories.isEmpty()) {
            throw RepositoryAccessException.actionable(
                    "No repositories are configured in zolt.toml.",
                    "Add [repositories] with at least one Maven-compatible repository URL.");
        }
        List<RepositoryAccess> access = new ArrayList<>();
        for (RepositorySettings repository : repositories) {
            access.add(new RepositoryAccess(
                    repository.id(),
                    repositoryUri(repository),
                    repository.credentials().map(credentialId -> authentication(config, repository, credentialId))));
        }
        return List.copyOf(access);
    }

    private static URI repositoryUri(RepositorySettings repository) {
        try {
            return RepositoryUrlPolicy.requireSafeUrl(
                    "Repository `" + repository.id() + "`",
                    repository.url(),
                    repository.credentials().isPresent());
        } catch (IllegalArgumentException exception) {
            throw new RepositoryAccessException(exception.getMessage(), exception);
        }
    }

    private RepositoryAuthentication authentication(
            ProjectConfig config,
            RepositorySettings repository,
            String credentialId) {
        RepositoryCredentialSettings credential = config.repositoryCredentials().get(credentialId);
        if (credential == null) {
            throw new RepositoryAccessException(
                    "Repository `"
                            + repository.id()
                            + "` references credentials `"
                            + credentialId
                            + "`, but [repositoryCredentials."
                            + credentialId
                            + "] is not defined.");
        }
        if (credential.usesToken()) {
            String tokenEnv = credential.tokenEnv().orElseThrow();
            String token = environment.apply(tokenEnv);
            if (token == null || token.isBlank()) {
                throw missingCredentials(repository, credentialId, List.of(tokenEnv));
            }
            return RepositoryAuthentication.bearer(token);
        }
        String usernameEnv = credential.usernameEnv().orElseThrow();
        String passwordEnv = credential.passwordEnv().orElseThrow();
        String username = environment.apply(usernameEnv);
        String password = environment.apply(passwordEnv);
        List<String> missing = new ArrayList<>();
        if (username == null || username.isBlank()) {
            missing.add(usernameEnv);
        }
        if (password == null || password.isBlank()) {
            missing.add(passwordEnv);
        }
        if (!missing.isEmpty()) {
            throw missingCredentials(repository, credentialId, missing);
        }
        return new RepositoryAuthentication(username, password);
    }

    private static RepositoryAccessException missingCredentials(
            RepositorySettings repository,
            String credentialId,
            List<String> missing) {
        return new RepositoryAccessException(
                "Repository `"
                        + repository.id()
                        + "` requires credentials `"
                        + credentialId
                        + "`, but environment variable"
                        + (missing.size() == 1 ? " " : "s ")
                        + String.join(", ", missing)
                        + (missing.size() == 1 ? " is" : " are")
                        + " not set. Set the variable"
                        + (missing.size() == 1 ? "" : "s")
                        + " and retry. Secret values are never written to zolt.lock or command output.");
    }
}
