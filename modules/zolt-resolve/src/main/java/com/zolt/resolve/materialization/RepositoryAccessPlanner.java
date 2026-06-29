package com.zolt.resolve.materialization;

import com.zolt.maven.RepositoryAuthentication;
import com.zolt.project.ProjectConfig;
import com.zolt.project.RepositoryCredentialSettings;
import com.zolt.project.RepositorySettings;
import com.zolt.project.RepositoryUrlPolicy;
import com.zolt.resolve.ResolveException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class RepositoryAccessPlanner {
    private static final String NO_REPOSITORIES_MESSAGE =
            "No repositories are configured in zolt.toml. Add [repositories] with at least one Maven-compatible repository URL.";

    private final Function<String, String> environment;

    RepositoryAccessPlanner() {
        this(System::getenv);
    }

    RepositoryAccessPlanner(Function<String, String> environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    List<RepositoryAccess> plan(ProjectConfig config) {
        List<RepositorySettings> repositories = config.repositorySettings().values().stream()
                .sorted(Comparator.comparing(RepositorySettings::id))
                .toList();
        if (repositories.isEmpty()) {
            throw new ResolveException(NO_REPOSITORIES_MESSAGE);
        }
        List<RepositoryAccess> access = new ArrayList<>();
        for (RepositorySettings repository : repositories) {
            access.add(new RepositoryAccess(
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
            throw new ResolveException(exception.getMessage(), exception);
        }
    }

    private RepositoryAuthentication authentication(
            ProjectConfig config,
            RepositorySettings repository,
            String credentialId) {
        RepositoryCredentialSettings credential = config.repositoryCredentials().get(credentialId);
        if (credential == null) {
            throw new ResolveException(
                    "Repository `"
                            + repository.id()
                            + "` references credentials `"
                            + credentialId
                            + "`, but [repositoryCredentials."
                            + credentialId
                            + "] is not defined.");
        }
        String username = environment.apply(credential.usernameEnv());
        String password = environment.apply(credential.passwordEnv());
        List<String> missing = new ArrayList<>();
        if (username == null || username.isBlank()) {
            missing.add(credential.usernameEnv());
        }
        if (password == null || password.isBlank()) {
            missing.add(credential.passwordEnv());
        }
        if (!missing.isEmpty()) {
            throw new ResolveException(
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
        return new RepositoryAuthentication(username, password);
    }
}
