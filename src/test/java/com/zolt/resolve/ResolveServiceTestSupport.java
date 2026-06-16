package com.zolt.resolve;

import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.ProjectConfig;
import com.zolt.quarkus.QuarkusDependencyRequestPlanner;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

abstract class ResolveServiceTestSupport extends ResolveServiceRepositoryTestSupport {
    final ResolveService resolveService = new ResolveService(new QuarkusDependencyRequestPlanner());
    final ZoltLockfileReader lockfileReader = new ZoltLockfileReader();

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

    ProjectConfig quarkusConfigWithDependencies(Map<String, String> dependencies) {
        return ResolveTestConfigs.quarkusConfigWithDependencies(baseUri, dependencies);
    }

    ProjectConfig quarkusPlatformConfigWithDependencies(Map<String, String> dependencies) {
        return ResolveTestConfigs.quarkusPlatformConfigWithDependencies(baseUri, dependencies);
    }

    ProjectConfig configWithTestDependencies(Map<String, String> testDependencies) {
        return ResolveTestConfigs.configWithTestDependencies(baseUri, testDependencies);
    }

    ProjectConfig platformConfig() {
        return ResolveTestConfigs.platformConfig(baseUri);
    }

    ProjectConfig platformVersionRefConfig(String alias) {
        return ResolveTestConfigs.platformVersionRefConfig(baseUri, alias);
    }

    ProjectConfig testPlatformConfig() {
        return ResolveTestConfigs.testPlatformConfig(baseUri);
    }

    ProjectConfig runtimeProvidedConfig() {
        return ResolveTestConfigs.runtimeProvidedConfig(baseUri);
    }

    ProjectConfig devConfig() {
        return ResolveTestConfigs.devConfig(baseUri);
    }

    ProjectConfig springBootPlatformConfig() {
        return ResolveTestConfigs.springBootPlatformConfig(baseUri);
    }

    ProjectConfig springBootWarPlatformConfig() {
        return ResolveTestConfigs.springBootWarPlatformConfig(baseUri);
    }

    ProjectConfig processorConfig() {
        return ResolveTestConfigs.processorConfig(baseUri);
    }

    ProjectConfig openApiConfig() {
        return ResolveTestConfigs.openApiConfig(baseUri);
    }

    ProjectConfig openApiVersionRefConfig(String alias) {
        return ResolveTestConfigs.openApiVersionRefConfig(baseUri, alias);
    }

    ProjectConfig configWithDependencyAndProcessor() {
        return ResolveTestConfigs.configWithDependencyAndProcessor(baseUri);
    }

    ProjectConfig processorPlatformConfig() {
        return ResolveTestConfigs.processorPlatformConfig(baseUri);
    }

    static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new AssertionError("Could not create test directory " + directory, exception);
        }
    }
}
