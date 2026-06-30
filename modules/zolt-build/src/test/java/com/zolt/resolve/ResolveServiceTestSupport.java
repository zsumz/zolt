package com.zolt.resolve;

import com.zolt.lockfile.toml.ZoltLockfileReader;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.net.URI;
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

    ProjectConfig platformConfig() {
        return ResolvePackagingTestConfigs.platformConfig(baseUri);
    }

    ProjectConfig platformVersionRefConfig(String alias) {
        return ResolvePackagingTestConfigs.platformVersionRefConfig(baseUri, alias);
    }

    ProjectConfig testPlatformConfig() {
        return ResolvePackagingTestConfigs.testPlatformConfig(baseUri);
    }

    ProjectConfig runtimeProvidedConfig() {
        return ResolvePackagingTestConfigs.runtimeProvidedConfig(baseUri);
    }

    ProjectConfig devConfig() {
        return ResolvePackagingTestConfigs.devConfig(baseUri);
    }

    ProjectConfig springBootPlatformConfig() {
        return ResolvePackagingTestConfigs.springBootPlatformConfig(baseUri);
    }

    ProjectConfig springBootWarPlatformConfig() {
        return ResolvePackagingTestConfigs.springBootWarPlatformConfig(baseUri);
    }

    ProjectConfig springBootNativePlatformConfig() {
        return ResolvePackagingTestConfigs.springBootNativePlatformConfig(baseUri);
    }

    ProjectConfig processorConfig() {
        return ResolveGeneratedSourceTestConfigs.processorConfig(baseUri);
    }

    ProjectConfig openApiConfig() {
        return ResolveGeneratedSourceTestConfigs.openApiConfig(baseUri);
    }

    ProjectConfig openApiVersionRefConfig(String alias) {
        return ResolveGeneratedSourceTestConfigs.openApiVersionRefConfig(baseUri, alias);
    }

    ProjectConfig configWithDependencyAndProcessor() {
        return ResolveGeneratedSourceTestConfigs.configWithDependencyAndProcessor(baseUri);
    }

    ProjectConfig processorPlatformConfig() {
        return ResolveGeneratedSourceTestConfigs.processorPlatformConfig(baseUri);
    }

    static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new AssertionError("Could not create test directory " + directory, exception);
        }
    }
}
