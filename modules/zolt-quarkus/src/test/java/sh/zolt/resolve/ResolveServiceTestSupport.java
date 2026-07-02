package sh.zolt.resolve;

import sh.zolt.lockfile.toml.ZoltLockfileReader;
import sh.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

abstract class ResolveServiceTestSupport extends ResolveServiceRepositoryTestSupport {
    final ResolveService resolveService = createResolveService();
    final ZoltLockfileReader lockfileReader = new ZoltLockfileReader();

    ResolveService createResolveService() {
        return new ResolveService();
    }

    ProjectConfig config() {
        return ResolveTestConfigs.config(baseUri);
    }

    ProjectConfig configWithDependencies(Map<String, String> dependencies) {
        return ResolveTestConfigs.configWithDependencies(baseUri, dependencies);
    }

    ProjectConfig configWithRepository(String repositoryUrl) {
        return ResolveTestConfigs.configWithRepository(repositoryUrl);
    }

    ProjectConfig configWithRepositoryAndDependencies(String repositoryUrl, Map<String, String> dependencies) {
        return ResolveTestConfigs.configWithRepositoryAndDependencies(repositoryUrl, dependencies);
    }

    ProjectConfig configWithTestDependencies(Map<String, String> testDependencies) {
        return ResolveTestConfigs.configWithTestDependencies(baseUri, testDependencies);
    }

    static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new AssertionError("Could not create test directory " + directory, exception);
        }
    }
}
