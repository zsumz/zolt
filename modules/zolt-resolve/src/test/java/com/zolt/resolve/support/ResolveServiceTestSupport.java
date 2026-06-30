package com.zolt.resolve.support;

import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveService;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public abstract class ResolveServiceTestSupport extends ResolveServiceRepositoryTestSupport {
    protected final ResolveService resolveService = createResolveService();
    protected final ZoltLockfileReader lockfileReader = new ZoltLockfileReader();

    protected ResolveService createResolveService() {
        return new ResolveService();
    }

    protected ProjectConfig config() {
        return ResolveTestConfigs.config(baseUri);
    }

    protected ProjectConfig configWithDependencies(Map<String, String> dependencies) {
        return ResolveTestConfigs.configWithDependencies(baseUri, dependencies);
    }

    protected ProjectConfig configWithRepository(String repositoryUrl) {
        return ResolveTestConfigs.configWithRepository(repositoryUrl);
    }

    protected ProjectConfig configWithRepositoryAndDependencies(String repositoryUrl, Map<String, String> dependencies) {
        return ResolveTestConfigs.configWithRepositoryAndDependencies(repositoryUrl, dependencies);
    }

    protected ProjectConfig configWithTestDependencies(Map<String, String> testDependencies) {
        return ResolveTestConfigs.configWithTestDependencies(baseUri, testDependencies);
    }

    protected ProjectConfig platformConfig() {
        return ResolvePackagingTestConfigs.platformConfig(baseUri);
    }

    protected ProjectConfig platformVersionRefConfig(String alias) {
        return ResolvePackagingTestConfigs.platformVersionRefConfig(baseUri, alias);
    }

    protected ProjectConfig testPlatformConfig() {
        return ResolvePackagingTestConfigs.testPlatformConfig(baseUri);
    }

    protected ProjectConfig runtimeProvidedConfig() {
        return ResolvePackagingTestConfigs.runtimeProvidedConfig(baseUri);
    }

    protected ProjectConfig devConfig() {
        return ResolvePackagingTestConfigs.devConfig(baseUri);
    }

    protected ProjectConfig springBootPlatformConfig() {
        return ResolvePackagingTestConfigs.springBootPlatformConfig(baseUri);
    }

    protected ProjectConfig springBootWarPlatformConfig() {
        return ResolvePackagingTestConfigs.springBootWarPlatformConfig(baseUri);
    }

    protected ProjectConfig springBootNativePlatformConfig() {
        return ResolvePackagingTestConfigs.springBootNativePlatformConfig(baseUri);
    }

    protected ProjectConfig processorConfig() {
        return ResolveGeneratedSourceTestConfigs.processorConfig(baseUri);
    }

    protected ProjectConfig openApiConfig() {
        return ResolveGeneratedSourceTestConfigs.openApiConfig(baseUri);
    }

    protected ProjectConfig openApiVersionRefConfig(String alias) {
        return ResolveGeneratedSourceTestConfigs.openApiVersionRefConfig(baseUri, alias);
    }

    protected ProjectConfig configWithDependencyAndProcessor() {
        return ResolveGeneratedSourceTestConfigs.configWithDependencyAndProcessor(baseUri);
    }

    protected ProjectConfig processorPlatformConfig() {
        return ResolveGeneratedSourceTestConfigs.processorPlatformConfig(baseUri);
    }

    protected static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new AssertionError("Could not create test directory " + directory, exception);
        }
    }
}
