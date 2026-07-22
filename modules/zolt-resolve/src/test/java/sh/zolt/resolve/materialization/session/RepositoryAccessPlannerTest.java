package sh.zolt.resolve.materialization.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.maven.repository.RepositoryAuthentication;
import sh.zolt.project.ProjectConfig;
import sh.zolt.resolve.ResolveException;
import sh.zolt.toml.ZoltTomlParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RepositoryAccessPlannerTest {
    @Test
    void plansRepositoriesByStableIdOrder() {
        List<RepositoryAccess> access = new RepositoryAccessPlanner().plan(config("""
                [repositories]
                zeta = "https://repo.example/zeta"
                alpha = "https://repo.example/alpha"
                """));

        assertEquals("https://repo.example/alpha", access.get(0).uri().toString());
        assertEquals("https://repo.example/zeta", access.get(1).uri().toString());
        assertTrue(access.get(0).authentication().isEmpty());
        assertTrue(access.get(1).authentication().isEmpty());
    }

    @Test
    void resolvesCredentialedRepositoryFromEnvironment() {
        RepositoryAccessPlanner planner = new RepositoryAccessPlanner(Map.of(
                "REPOSITORY_USERNAME",
                "user",
                "REPOSITORY_PASSWORD",
                "secret")::get);

        RepositoryAccess access = planner.plan(config("""
                [repositories]
                company = { url = "https://repo.example/company", credentials = "company-artifactory" }

                [repositoryCredentials.company-artifactory]
                usernameEnv = "REPOSITORY_USERNAME"
                passwordEnv = "REPOSITORY_PASSWORD"
                """)).getFirst();

        RepositoryAuthentication authentication = access.authentication().orElseThrow();
        assertEquals("https://repo.example/company", access.uri().toString());
        assertEquals(
                "Basic " + Base64.getEncoder().encodeToString("user:secret".getBytes(StandardCharsets.UTF_8)),
                authentication.authorizationHeaderValue());
    }

    @Test
    void resolvesBearerTokenCredentialFromEnvironment() {
        RepositoryAccessPlanner planner = new RepositoryAccessPlanner(Map.of("REPOSITORY_TOKEN", "pat-xyz")::get);

        RepositoryAccess access = planner.plan(config("""
                [repositories]
                company = { url = "https://repo.example/company", credentials = "company-artifactory" }

                [repositoryCredentials.company-artifactory]
                tokenEnv = "REPOSITORY_TOKEN"
                """)).getFirst();

        assertEquals("Bearer pat-xyz", access.authentication().orElseThrow().authorizationHeaderValue());
    }

    @Test
    void rejectsRepositoryUrlUserinfoBeforeResolution() {
        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> new RepositoryAccessPlanner().plan(config("""
                        [repositories]
                        company = "https://user:super-secret@repo.example/company"
                        """)));

        assertTrue(exception.getMessage().contains("Repository `company` URL contains embedded credentials"));
        assertTrue(exception.getMessage().contains("Move credentials to [repositoryCredentials] environment references"));
        assertTrue(!exception.getMessage().contains("user:super-secret"));
        assertTrue(!exception.getMessage().contains("super-secret"));
    }

    @Test
    void rejectsCredentialedRemoteHttpRepository() {
        RepositoryAccessPlanner planner = new RepositoryAccessPlanner(Map.of(
                "REPOSITORY_USERNAME",
                "user",
                "REPOSITORY_PASSWORD",
                "secret")::get);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> planner.plan(config("""
                        [repositories]
                        company = { url = "http://repo.example/company", credentials = "company-artifactory" }

                        [repositoryCredentials.company-artifactory]
                        usernameEnv = "REPOSITORY_USERNAME"
                        passwordEnv = "REPOSITORY_PASSWORD"
                        """)));

        assertTrue(exception.getMessage().contains("Repository `company` uses credentials with an insecure remote repository URL"));
        assertTrue(exception.getMessage().contains("Credentialed remote repositories require HTTPS"));
    }

    @Test
    void rejectsNonLocalHttpRepository() {
        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> new RepositoryAccessPlanner().plan(config("""
                        [repositories]
                        company = "http://repo.example/company"
                        """)));

        assertTrue(exception.getMessage().contains("Repository `company` uses non-local HTTP"));
        assertTrue(exception.getMessage().contains("plain HTTP is allowed only for localhost or loopback"));
    }

    @Test
    void allowsLoopbackHttpRepositoryForLocalDevelopment() {
        List<RepositoryAccess> access = new RepositoryAccessPlanner().plan(config("""
                [repositories]
                local = "http://127.0.0.1:18080/maven2"
                """));

        assertEquals("http://127.0.0.1:18080/maven2", access.getFirst().uri().toString());
    }

    @Test
    void reportsMissingCredentialDefinition() {
        ProjectConfig config = withoutRepositoryCredentials(config("""
                [repositories]
                company = { url = "https://repo.example/company", credentials = "company-artifactory" }

                [repositoryCredentials.company-artifactory]
                usernameEnv = "REPOSITORY_USERNAME"
                passwordEnv = "REPOSITORY_PASSWORD"
                """));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> new RepositoryAccessPlanner().plan(config));

        assertTrue(exception.getMessage().contains("Repository `company` references credentials `company-artifactory`"));
        assertTrue(exception.getMessage().contains("[repositoryCredentials.company-artifactory] is not defined"));
    }

    @Test
    void reportsMissingCredentialEnvironmentWithoutSecretValues() {
        RepositoryAccessPlanner planner = new RepositoryAccessPlanner(Map.of(
                "REPOSITORY_USERNAME",
                "user")::get);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> planner.plan(config("""
                        [repositories]
                        company = { url = "https://repo.example/company", credentials = "company-artifactory" }

                        [repositoryCredentials.company-artifactory]
                        usernameEnv = "REPOSITORY_USERNAME"
                        passwordEnv = "REPOSITORY_PASSWORD"
                        """)));

        assertTrue(exception.getMessage().contains("Repository `company` requires credentials `company-artifactory`"));
        assertTrue(exception.getMessage().contains("REPOSITORY_PASSWORD"));
        assertTrue(exception.getMessage().contains("Secret values are never written to zolt.lock or command output."));
    }

    private static ProjectConfig config(String repositoryToml) {
        return new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                %s
                """.formatted(repositoryToml));
    }

    private static ProjectConfig withoutRepositoryCredentials(ProjectConfig config) {
        return new ProjectConfig(
                config.project(),
                config.repositories(),
                config.repositorySettings(),
                Map.of(),
                config.versionAliases(),
                config.platforms(),
                config.apiDependencies(),
                config.managedApiDependencies(),
                config.workspaceApiDependencies(),
                config.dependencies(),
                config.managedDependencies(),
                config.workspaceDependencies(),
                config.runtimeDependencies(),
                config.managedRuntimeDependencies(),
                config.providedDependencies(),
                config.managedProvidedDependencies(),
                config.devDependencies(),
                config.managedDevDependencies(),
                config.testDependencies(),
                config.managedTestDependencies(),
                config.workspaceTestDependencies(),
                config.annotationProcessors(),
                config.managedAnnotationProcessors(),
                config.workspaceAnnotationProcessors(),
                config.testAnnotationProcessors(),
                config.managedTestAnnotationProcessors(),
                config.workspaceTestAnnotationProcessors(),
                config.dependencyPolicy(),
                config.build(),
                config.nativeSettings(),
                config.compilerSettings(),
                config.packageSettings(),
                config.frameworkSettings(),
                config.dependencyMetadata());
    }
}
