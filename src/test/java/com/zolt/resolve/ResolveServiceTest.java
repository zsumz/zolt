package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zolt.cache.ArtifactCacheException;
import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResolveServiceTest {
    private final ResolveService resolveService = new ResolveService();
    private final ZoltLockfileReader lockfileReader = new ZoltLockfileReader();
    private final Map<String, byte[]> responses = new HashMap<>();

    @TempDir
    private Path tempDir;

    private HttpServer server;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/maven2/");
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>lib</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "lib", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>lib</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void resolveDownloadsArtifactsAndWritesLockfile() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, config(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        assertEquals(4, result.downloadCount());
        assertEquals(0, result.conflictCount());
        assertEquals(projectDir.resolve("zolt.lock"), result.lockfilePath());
        assertTrue(Files.exists(result.lockfilePath()));

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertEquals(2, lockfile.packages().size());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app")) && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "lib")) && !lockPackage.direct()));
    }

    @Test
    void repeatedResolveUsesCachedArtifacts() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult first = resolveService.resolve(projectDir, config(), cacheRoot);
        ResolveResult second = resolveService.resolve(projectDir, config(), cacheRoot);

        assertEquals(4, first.downloadCount());
        assertEquals(0, second.downloadCount());
    }

    @Test
    void lockedResolveSucceedsWhenLockfileMatches() throws IOException {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        resolveService.resolve(projectDir, config(), cacheRoot);
        String existingLockfile = Files.readString(projectDir.resolve("zolt.lock"));

        ResolveResult result = resolveService.resolve(projectDir, config(), cacheRoot, true);

        assertEquals(2, result.resolvedCount());
        assertEquals(existingLockfile, Files.readString(projectDir.resolve("zolt.lock")));
    }

    @Test
    void lockedResolveFailsWhenLockfileIsMissing() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, config(), cacheRoot, true));

        assertTrue(exception.getMessage().contains("Locked resolve requires zolt.lock"));
        assertTrue(exception.getMessage().contains("Run `zolt resolve` to create it"));
    }

    @Test
    void lockedResolveFailsWhenLockfileWouldChange() throws IOException {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        resolveService.resolve(projectDir, config(), cacheRoot);
        String existingLockfile = Files.readString(projectDir.resolve("zolt.lock"));
        addArtifact("com.example", "extra", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>extra</artifactId>
                  <version>1.0.0</version>
                </project>
                """);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, configWithDependencies(Map.of(
                        "com.example:app", "1.0.0",
                        "com.example:extra", "1.0.0")), cacheRoot, true));

        assertTrue(exception.getMessage().contains("zolt.lock is out of date"));
        assertTrue(exception.getMessage().contains("Run `zolt resolve` to refresh it"));
        assertEquals(existingLockfile, Files.readString(projectDir.resolve("zolt.lock")));
    }

    @Test
    void offlineResolveUsesCachedArtifactsWithoutFetching() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        resolveService.resolve(projectDir, config(), cacheRoot);
        responses.clear();

        ResolveResult result = resolveService.resolve(projectDir, config(), cacheRoot, false, true);

        assertEquals(2, result.resolvedCount());
        assertEquals(0, result.downloadCount());
    }

    @Test
    void offlineResolveFailsClearlyWhenArtifactIsMissingFromCache() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ArtifactCacheException exception = assertThrows(
                ArtifactCacheException.class,
                () -> resolveService.resolve(projectDir, config(), cacheRoot, false, true));

        assertTrue(exception.getMessage().contains("Offline mode requires cached POM"));
        assertTrue(exception.getMessage().contains("Run the command without --offline"));
    }

    @Test
    void importedBomProvidesManagedVersionForTransitiveDependency() {
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <bom.version>1.0.0</bom.version>
                  </properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>bom</artifactId>
                        <version>${bom.version}</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>managed-lib</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addPom("com.example", "bom", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>bom</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <managed-lib.version>2.0.0</managed-lib.version>
                  </properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>managed-lib</artifactId>
                        <version>${managed-lib.version}</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addArtifact("com.example", "managed-lib", "2.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>managed-lib</artifactId>
                  <version>2.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, config(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        assertEquals(5, result.downloadCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "managed-lib"))
                        && lockPackage.version().equals("2.0.0")));
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "bom"))));
    }

    @Test
    void importedBomMissingVersionFailsClearly() {
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>bom</artifactId>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>managed-lib</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, config(), cacheRoot));

        assertTrue(exception.getMessage().contains("Imported BOM com.example:bom"));
        assertTrue(exception.getMessage().contains("is missing a version"));
    }

    @Test
    void importedBomCycleFailsClearly() {
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>bom-a</artifactId>
                        <version>1.0.0</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>managed-lib</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addPom("com.example", "bom-a", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>bom-a</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>bom-b</artifactId>
                        <version>1.0.0</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addPom("com.example", "bom-b", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>bom-b</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>bom-a</artifactId>
                        <version>1.0.0</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, config(), cacheRoot));

        assertTrue(exception.getMessage().contains("Imported BOM cycle detected"));
        assertTrue(exception.getMessage().contains("com.example:bom-a:1.0.0"));
        assertTrue(exception.getMessage().contains("com.example:bom-b:1.0.0"));
    }

    @Test
    void projectPlatformProvidesManagedVersionForDirectDependency() {
        addPom("com.example", "platform", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>platform</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>app</artifactId>
                        <version>1.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, platformConfig(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        assertEquals(5, result.downloadCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app"))
                        && lockPackage.version().equals("1.0.0")
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "platform"))));
    }

    @Test
    void projectPlatformProvidesManagedVersionForDirectTestDependency() {
        addPom("com.example", "platform", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>platform</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>app</artifactId>
                        <version>1.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, testPlatformConfig(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app"))
                        && lockPackage.version().equals("1.0.0")
                        && lockPackage.scope() == DependencyScope.TEST
                        && lockPackage.direct()));
    }

    @Test
    void annotationProcessorsResolveToProcessorScopesOnly() {
        addArtifact("com.example", "processor", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>processor</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>processor-helper</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "processor-helper", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>processor-helper</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, processorConfig(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "processor"))
                        && lockPackage.scope() == DependencyScope.PROCESSOR
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "processor-helper"))
                        && lockPackage.scope() == DependencyScope.PROCESSOR
                        && !lockPackage.direct()));

        ClasspathSet classpaths = new ClasspathBuilder().build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        assertEquals(List.of(), classpaths.compile().entries());
        assertEquals(List.of(), classpaths.runtime().entries());
        assertEquals(List.of(), classpaths.test().entries());
        assertEquals(List.of(
                cacheRoot.resolve("com/example/processor-helper/1.0.0/processor-helper-1.0.0.jar"),
                cacheRoot.resolve("com/example/processor/1.0.0/processor-1.0.0.jar")),
                classpaths.processor().entries());
        assertEquals(List.of(), classpaths.testProcessor().entries());
    }

    @Test
    void selectedVersionKeepsAllParticipatingScopes() {
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>shared</artifactId>
                      <version>2.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "processor", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>processor</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>shared</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "shared", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>shared</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        addArtifact("com.example", "shared", "2.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>shared</artifactId>
                  <version>2.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, configWithDependencyAndProcessor(), cacheRoot);

        assertEquals(4, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "shared"))
                        && lockPackage.version().equals("2.0.0")
                        && lockPackage.scope() == DependencyScope.COMPILE));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "shared"))
                        && lockPackage.version().equals("2.0.0")
                        && lockPackage.scope() == DependencyScope.PROCESSOR));

        ClasspathSet classpaths = new ClasspathBuilder().build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        assertTrue(classpaths.processor().entries().contains(
                cacheRoot.resolve("com/example/shared/2.0.0/shared-2.0.0.jar")));
    }

    @Test
    void projectPlatformProvidesManagedVersionForAnnotationProcessor() {
        addPom("com.example", "platform", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>platform</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>processor</artifactId>
                        <version>1.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addArtifact("com.example", "processor", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>processor</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, processorPlatformConfig(), cacheRoot);

        assertEquals(1, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "processor"))
                        && lockPackage.version().equals("1.0.0")
                        && lockPackage.scope() == DependencyScope.PROCESSOR
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "platform"))));
    }

    @Test
    void unmanagedDirectDependencyFailsClearly() {
        addPom("com.example", "platform", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>platform</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>other</artifactId>
                        <version>1.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, platformConfig(), cacheRoot));

        assertTrue(exception.getMessage().contains("Dependency com.example:app in [dependencies]"));
        assertTrue(exception.getMessage().contains("uses a platform-managed version"));
    }

    @Test
    void unmanagedAnnotationProcessorFailsClearly() {
        addPom("com.example", "platform", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>platform</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>other</artifactId>
                        <version>1.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, processorPlatformConfig(), cacheRoot));

        assertTrue(exception.getMessage().contains("Dependency com.example:processor in [annotationProcessors]"));
        assertTrue(exception.getMessage().contains("uses a platform-managed version"));
    }

    private ProjectConfig config() {
        return configWithDependencies(Map.of("com.example:app", "1.0.0"));
    }

    private ProjectConfig configWithDependencies(Map<String, String> dependencies) {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                dependencies,
                Map.of(),
                BuildSettings.defaults());
    }

    private ProjectConfig platformConfig() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of("com.example:platform", "1.0.0"),
                Map.of(),
                Set.of("com.example:app"),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null);
    }

    private ProjectConfig testPlatformConfig() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of("com.example:platform", "1.0.0"),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of("com.example:app"),
                BuildSettings.defaults(),
                null);
    }

    private ProjectConfig processorConfig() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of("com.example:processor", "1.0.0"),
                Set.of(),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null);
    }

    private ProjectConfig configWithDependencyAndProcessor() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of(),
                Map.of("com.example:app", "1.0.0"),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of("com.example:processor", "1.0.0"),
                Set.of(),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null);
    }

    private ProjectConfig processorPlatformConfig() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of("com.example:platform", "1.0.0"),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of("com.example:processor"),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null);
    }

    private static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new AssertionError("Could not create test directory " + directory, exception);
        }
    }

    private void addArtifact(String groupId, String artifactId, String version, String pom) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + ".pom", pom.getBytes(StandardCharsets.UTF_8));
        responses.put(base + ".jar", new byte[] {0x50, 0x4b, 0x03, 0x04});
    }

    private void addPom(String groupId, String artifactId, String version, String pom) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + ".pom", pom.getBytes(StandardCharsets.UTF_8));
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body = responses.get(exchange.getRequestURI().getPath());
        if (body == null) {
            respond(exchange, 404, "missing".getBytes(StandardCharsets.UTF_8));
            return;
        }
        respond(exchange, 200, body);
    }

    private static void respond(HttpExchange exchange, int statusCode, byte[] body) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
