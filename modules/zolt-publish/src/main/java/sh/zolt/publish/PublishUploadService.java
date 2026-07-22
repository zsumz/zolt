package sh.zolt.publish;

import sh.zolt.maven.ArtifactDescriptor;
import sh.zolt.maven.Coordinate;
import sh.zolt.maven.repository.MavenRepositoryClient;
import sh.zolt.maven.repository.RepositoryAuthentication;
import sh.zolt.maven.repository.RepositoryClientException;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.RepositoryCredentialSettings;
import sh.zolt.project.RepositoryUrlPolicy;
import sh.zolt.toml.ZoltTomlParser;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

public final class PublishUploadService {
    private final PublishDryRunService dryRunService;
    private final ZoltTomlParser projectParser;
    private final PublishSettingsReader publishSettingsReader;
    private final MavenRepositoryClient repositoryClient;
    private final Function<String, String> environment;

    public PublishUploadService() {
        this(
                new PublishDryRunService(),
                new ZoltTomlParser(),
                new PublishSettingsReader(),
                new MavenRepositoryClient(),
                System::getenv);
    }

    PublishUploadService(
            PublishDryRunService dryRunService,
            ZoltTomlParser projectParser,
            PublishSettingsReader publishSettingsReader,
            MavenRepositoryClient repositoryClient,
            Function<String, String> environment) {
        this.dryRunService = dryRunService;
        this.projectParser = projectParser;
        this.publishSettingsReader = publishSettingsReader;
        this.repositoryClient = repositoryClient;
        this.environment = environment;
    }

    public PublishUploadResult upload(Path projectRoot) {
        Path root = projectRoot.toAbsolutePath().normalize();
        PublishDryRunPlan plan = dryRunService.plan(root);
        if (!plan.ok()) {
            throw new PublishException("Publish is blocked. Run `zolt publish --dry-run` and resolve the reported blockers before uploading.");
        }
        ProjectConfig config = projectParser.parse(root.resolve("zolt.toml"));
        PublishSettings settings = publishSettingsReader.read(root.resolve("zolt.toml"), config.repositoryCredentials());
        PublishRepositorySettings repository = selectedRepository(settings, plan);
        Coordinate coordinate = coordinate(config);
        Optional<RepositoryAuthentication> authentication = authentication(repository, config);
        URI repositoryUri = repositoryUri(repository);
        try {
            repositoryClient.uploadArtifact(
                    repositoryUri,
                    new ArtifactDescriptor(coordinate, Optional.empty(), extension(plan.artifactPath())),
                    root.resolve(plan.artifactPath()).normalize(),
                    authentication);
            for (PublishArtifactPlan artifact : plan.supplementalArtifacts()) {
                repositoryClient.uploadArtifact(
                        repositoryUri,
                        new ArtifactDescriptor(coordinate, artifact.classifier(), extension(artifact.path())),
                        root.resolve(artifact.path()).normalize(),
                        authentication);
            }
            repositoryClient.uploadPom(
                    repositoryUri,
                    coordinate,
                    root.resolve(plan.pomPath()).normalize(),
                    authentication);
        } catch (IllegalArgumentException exception) {
            throw new PublishException(
                    "Publish repository `" + repository.id() + "` has an invalid URL. Use a Maven-compatible repository URL.",
                    exception);
        } catch (RepositoryClientException exception) {
            throw new PublishException(exception.getMessage(), exception);
        }
        return new PublishUploadResult(plan);
    }

    private static URI repositoryUri(PublishRepositorySettings repository) {
        try {
            return RepositoryUrlPolicy.requireSafeUrl(
                    "Publish repository `" + repository.id() + "`",
                    repository.url(),
                    repository.credentials().isPresent());
        } catch (IllegalArgumentException exception) {
            throw new PublishException(exception.getMessage(), exception);
        }
    }

    private static PublishRepositorySettings selectedRepository(
            PublishSettings settings,
            PublishDryRunPlan plan) {
        PublishRepositorySettings repository = settings.repositories().get(plan.repositoryId());
        if (repository == null) {
            throw new PublishException("Publish repository `" + plan.repositoryId() + "` is not defined.");
        }
        return repository;
    }

    private Optional<RepositoryAuthentication> authentication(
            PublishRepositorySettings repository,
            ProjectConfig config) {
        if (repository.credentials().isEmpty()) {
            return RepositoryAuthentication.none();
        }
        RepositoryCredentialSettings credential = config.repositoryCredentials().get(repository.credentials().orElseThrow());
        if (credential == null) {
            throw new PublishException("missing credential metadata for publish repository `" + repository.id() + "`");
        }
        if (credential.usesToken()) {
            String token = environment.apply(credential.tokenEnv().orElseThrow());
            if (token == null || token.isBlank()) {
                throw new PublishException("missing credential environment variables for publish repository `" + repository.id() + "`");
            }
            return Optional.of(RepositoryAuthentication.bearer(token));
        }
        String username = environment.apply(credential.usernameEnv().orElseThrow());
        String password = environment.apply(credential.passwordEnv().orElseThrow());
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new PublishException("missing credential environment variables for publish repository `" + repository.id() + "`");
        }
        return Optional.of(new RepositoryAuthentication(username, password));
    }

    private static Coordinate coordinate(ProjectConfig config) {
        return new Coordinate(
                config.project().group(),
                config.project().name(),
                Optional.of(config.project().version()));
    }

    private static String extension(Path artifactPath) {
        String fileName = artifactPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new PublishException("Could not determine publish artifact extension from `" + fileName + "`.");
        }
        return fileName.substring(dot + 1);
    }
}
