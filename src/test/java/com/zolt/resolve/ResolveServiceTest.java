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
import com.zolt.project.FrameworkSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import java.io.ByteArrayOutputStream;
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
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
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
    void importedBomIgnoresTestScopedManagedDependencyWithUnresolvedProperty() {
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
        addPom("com.example", "bom", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>bom</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>managed-lib</artifactId>
                        <version>2.0.0</version>
                      </dependency>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>test-tooling</artifactId>
                        <version>${missing.test.version}</version>
                        <scope>test</scope>
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
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "managed-lib"))
                        && lockPackage.version().equals("2.0.0")));
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "test-tooling"))));
    }

    @Test
    void importedBomManagedTestScopeSkipsVersionlessTransitiveTestDependency() {
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
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>test-bom</artifactId>
                        <version>1.0.0</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>junit</groupId>
                      <artifactId>junit</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addPom("com.example", "test-bom", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>test-bom</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.13.2</version>
                        <scope>test</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, config(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("junit", "junit"))));
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
    void springBootPackageModeAddsLoaderFromProjectPlatform() {
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
                      <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-loader</artifactId>
                        <version>4.0.6</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addArtifact("org.springframework.boot", "spring-boot-loader", "4.0.6", """
                <project>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-loader</artifactId>
                  <version>4.0.6</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, springBootPlatformConfig(), cacheRoot);

        assertEquals(3, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app"))
                        && lockPackage.scope() == DependencyScope.COMPILE
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.springframework.boot", "spring-boot-loader"))
                        && lockPackage.version().equals("4.0.6")
                        && lockPackage.scope() == DependencyScope.RUNTIME
                        && !lockPackage.direct()));
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "platform"))));
    }

    @Test
    void springBootPackageModeKeepsExplicitLoaderDependencyDirect() {
        addArtifact("org.springframework.boot", "spring-boot-loader", "4.0.6", """
                <project>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-loader</artifactId>
                  <version>4.0.6</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                configWithDependencies(Map.of("org.springframework.boot:spring-boot-loader", "4.0.6"))
                        .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT)),
                cacheRoot);

        assertEquals(1, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.springframework.boot", "spring-boot-loader"))
                        && lockPackage.scope() == DependencyScope.COMPILE
                        && lockPackage.direct()));
    }

    @Test
    void directRuntimeAndProvidedDependenciesUseDistinctScopes() {
        addArtifact("com.example", "runtime-tool", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>runtime-tool</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        addArtifact("jakarta.servlet", "jakarta.servlet-api", "6.1.0", """
                <project>
                  <groupId>jakarta.servlet</groupId>
                  <artifactId>jakarta.servlet-api</artifactId>
                  <version>6.1.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, runtimeProvidedConfig(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "runtime-tool"))
                        && lockPackage.scope() == DependencyScope.RUNTIME
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("jakarta.servlet", "jakarta.servlet-api"))
                        && lockPackage.scope() == DependencyScope.PROVIDED
                        && lockPackage.direct()));

        ClasspathSet classpaths = new ClasspathBuilder().build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        assertEquals(List.of(
                cacheRoot.resolve("jakarta/servlet/jakarta.servlet-api/6.1.0/jakarta.servlet-api-6.1.0.jar")),
                classpaths.compile().entries());
        assertEquals(List.of(
                cacheRoot.resolve("com/example/runtime-tool/1.0.0/runtime-tool-1.0.0.jar")),
                classpaths.runtime().entries());
    }

    @Test
    void directDevDependenciesAndTransitivesUseDevScope() {
        addArtifact("com.example", "devtools", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>devtools</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>dev-helper</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "dev-helper", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>dev-helper</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, devConfig(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "devtools"))
                        && lockPackage.scope() == DependencyScope.DEV
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "dev-helper"))
                        && lockPackage.scope() == DependencyScope.DEV
                        && !lockPackage.direct()));

        ClasspathSet classpaths = new ClasspathBuilder().build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        assertEquals(List.of(), classpaths.compile().entries());
        assertEquals(List.of(
                cacheRoot.resolve("com/example/dev-helper/1.0.0/dev-helper-1.0.0.jar"),
                cacheRoot.resolve("com/example/devtools/1.0.0/devtools-1.0.0.jar")),
                classpaths.runtime().entries());
    }

    @Test
    void springBootPackageModeFailsClearlyWithoutManagedLoaderVersion() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(
                        projectDir,
                        config().withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT)),
                        cacheRoot));

        assertTrue(exception.getMessage().contains("Spring Boot package mode requires package tool artifact"));
        assertTrue(exception.getMessage().contains("Add the Spring Boot platform to [platforms]"));
        assertTrue(exception.getMessage().contains("run `zolt resolve`"));
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
        addJUnitConsoleArtifact("1.11.4");
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, testPlatformConfig(), cacheRoot);

        assertEquals(3, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app"))
                        && lockPackage.version().equals("1.0.0")
                        && lockPackage.scope() == DependencyScope.TEST
                        && lockPackage.direct()));
    }

    @Test
    void testDependenciesAddJUnitPlatformConsoleRunnerTooling() {
        addArtifact("org.junit.platform", "junit-platform-console", "1.11.4", """
                <project>
                  <groupId>org.junit.platform</groupId>
                  <artifactId>junit-platform-console</artifactId>
                  <version>1.11.4</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.junit.platform</groupId>
                      <artifactId>junit-platform-launcher</artifactId>
                      <version>1.11.4</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("org.junit.platform", "junit-platform-launcher", "1.11.4", """
                <project>
                  <groupId>org.junit.platform</groupId>
                  <artifactId>junit-platform-launcher</artifactId>
                  <version>1.11.4</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                configWithTestDependencies(Map.of("com.example:app", "1.0.0")),
                cacheRoot);

        assertEquals(4, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app"))
                        && lockPackage.scope() == DependencyScope.TEST
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.junit.platform", "junit-platform-console"))
                        && lockPackage.version().equals("1.11.4")
                        && lockPackage.scope() == DependencyScope.TEST
                        && !lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.junit.platform", "junit-platform-launcher"))
                        && lockPackage.version().equals("1.11.4")
                        && lockPackage.scope() == DependencyScope.TEST
                        && !lockPackage.direct()));
    }

    @Test
    void testRunnerToolingUsesProjectManagedConsoleVersion() {
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
                      <dependency>
                        <groupId>org.junit.platform</groupId>
                        <artifactId>junit-platform-console</artifactId>
                        <version>1.10.2</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addJUnitConsoleArtifact("1.10.2");
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, testPlatformConfig(), cacheRoot);

        assertEquals(3, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.junit.platform", "junit-platform-console"))
                        && lockPackage.version().equals("1.10.2")
                        && lockPackage.scope() == DependencyScope.TEST
                        && !lockPackage.direct()));
    }

    @Test
    void explicitJUnitPlatformConsoleDependencyStaysDirect() {
        addArtifact("org.junit.platform", "junit-platform-console-standalone", "1.11.4", """
                <project>
                  <groupId>org.junit.platform</groupId>
                  <artifactId>junit-platform-console-standalone</artifactId>
                  <version>1.11.4</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                configWithTestDependencies(Map.of("org.junit.platform:junit-platform-console-standalone", "1.11.4")),
                cacheRoot);

        assertEquals(1, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.junit.platform", "junit-platform-console-standalone"))
                        && lockPackage.scope() == DependencyScope.TEST
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.junit.platform", "junit-platform-console"))));
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
    void quarkusRuntimeExtensionAddsDeploymentArtifactScope() {
        addArtifact("io.quarkus", "quarkus-rest", "3.33.0", """
                <project>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-rest</artifactId>
                  <version>3.33.0</version>
                </project>
                """, Map.of(
                "META-INF/quarkus-extension.properties",
                """
                deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0
                provides-capabilities=io.quarkus.rest
                """));
        addArtifact("io.quarkus", "quarkus-rest-deployment", "3.33.0", """
                <project>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-rest-deployment</artifactId>
                  <version>3.33.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>io.quarkus</groupId>
                      <artifactId>quarkus-core-deployment</artifactId>
                      <version>3.33.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("io.quarkus", "quarkus-core-deployment", "3.33.0", """
                <project>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-core-deployment</artifactId>
                  <version>3.33.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                quarkusConfigWithDependencies(Map.of("io.quarkus:quarkus-rest", "3.33.0")),
                cacheRoot);

        assertEquals(3, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("io.quarkus", "quarkus-rest"))
                        && lockPackage.scope() == DependencyScope.COMPILE
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("io.quarkus", "quarkus-rest-deployment"))
                        && lockPackage.scope() == DependencyScope.QUARKUS_DEPLOYMENT
                        && !lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("io.quarkus", "quarkus-core-deployment"))
                        && lockPackage.scope() == DependencyScope.QUARKUS_DEPLOYMENT
                        && !lockPackage.direct()));

        ClasspathSet classpaths = new ClasspathBuilder().build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        assertEquals(List.of(
                cacheRoot.resolve("io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar")),
                classpaths.compile().entries());
        assertEquals(List.of(
                cacheRoot.resolve("io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar")),
                classpaths.runtime().entries());
        assertTrue(classpaths.runtime().entries().stream()
                .noneMatch(path -> path.toString().contains("deployment")));
        assertEquals(List.of(), classpaths.processor().entries());
        assertEquals(List.of(), classpaths.testProcessor().entries());
    }

    @Test
    void quarkusMetadataParentFirstArtifactsEnterDeploymentClasspathWhenVersionIsManaged() {
        addPom("io.quarkus.platform", "quarkus-bom", "3.33.0", """
                <project>
                  <groupId>io.quarkus.platform</groupId>
                  <artifactId>quarkus-bom</artifactId>
                  <version>3.33.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>io.quarkus</groupId>
                        <artifactId>quarkus-rest</artifactId>
                        <version>3.33.0</version>
                      </dependency>
                      <dependency>
                        <groupId>io.quarkus</groupId>
                        <artifactId>quarkus-rest-deployment</artifactId>
                        <version>3.33.0</version>
                      </dependency>
                      <dependency>
                        <groupId>io.quarkus</groupId>
                        <artifactId>quarkus-bootstrap-maven-resolver</artifactId>
                        <version>3.33.0</version>
                      </dependency>
                      <dependency>
                        <groupId>org.jacoco</groupId>
                        <artifactId>org.jacoco.agent</artifactId>
                        <version>0.8.14</version>
                      </dependency>
                      <dependency>
                        <groupId>org.jacoco</groupId>
                        <artifactId>org.jacoco.agent</artifactId>
                        <version>0.8.14</version>
                        <classifier>runtime</classifier>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addArtifact("io.quarkus", "quarkus-rest", "3.33.0", """
                <project>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-rest</artifactId>
                  <version>3.33.0</version>
                </project>
                """, Map.of(
                "META-INF/quarkus-extension.properties",
                """
                deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0
                parent-first-artifacts=io.quarkus:quarkus-bootstrap-maven-resolver,org.jacoco:org.jacoco.agent:runtime
                """));
        addArtifact("io.quarkus", "quarkus-rest-deployment", "3.33.0", """
                <project>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-rest-deployment</artifactId>
                  <version>3.33.0</version>
                </project>
                """);
        addArtifact("io.quarkus", "quarkus-bootstrap-maven-resolver", "3.33.0", """
                <project>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-bootstrap-maven-resolver</artifactId>
                  <version>3.33.0</version>
                </project>
                """);
        addArtifact("org.jacoco", "org.jacoco.agent", "0.8.14", """
                <project>
                  <groupId>org.jacoco</groupId>
                  <artifactId>org.jacoco.agent</artifactId>
                  <version>0.8.14</version>
                </project>
                """);
        addClassifierJar("org.jacoco", "org.jacoco.agent", "0.8.14", "runtime", Map.of());
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                quarkusPlatformConfigWithDependencies(Map.of("io.quarkus:quarkus-rest", "")),
                cacheRoot);

        assertEquals(4, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("io.quarkus", "quarkus-bootstrap-maven-resolver"))
                        && lockPackage.scope() == DependencyScope.QUARKUS_DEPLOYMENT));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.jacoco", "org.jacoco.agent"))
                        && lockPackage.scope() == DependencyScope.QUARKUS_DEPLOYMENT
                        && lockPackage.jar().orElseThrow().equals(
                                "org/jacoco/org.jacoco.agent/0.8.14/org.jacoco.agent-0.8.14-runtime.jar")));
    }

    @Test
    void quarkusRuntimeExtensionDoesNotAddDeploymentArtifactScopeUnlessEnabled() {
        addArtifact("io.quarkus", "quarkus-rest", "3.33.0", """
                <project>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-rest</artifactId>
                  <version>3.33.0</version>
                </project>
                """, Map.of(
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0\n"));
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                configWithDependencies(Map.of("io.quarkus:quarkus-rest", "3.33.0")),
                cacheRoot);

        assertEquals(1, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.scope() == DependencyScope.QUARKUS_DEPLOYMENT));
    }

    @Test
    void quarkusDeploymentArtifactWithClassifierResolvesClassifierJarPath() {
        addArtifact("io.quarkus", "quarkus-custom", "1.0.0", """
                <project>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-custom</artifactId>
                  <version>1.0.0</version>
                </project>
                """, Map.of(
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-custom-deployment:deployment:jar:1.0.0\n"));
        addArtifact("io.quarkus", "quarkus-custom-deployment", "1.0.0", """
                <project>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-custom-deployment</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        addClassifierJar("io.quarkus", "quarkus-custom-deployment", "1.0.0", "deployment", Map.of());
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                quarkusConfigWithDependencies(Map.of("io.quarkus:quarkus-custom", "1.0.0")),
                cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("io.quarkus", "quarkus-custom-deployment"))
                        && lockPackage.scope() == DependencyScope.QUARKUS_DEPLOYMENT
                        && lockPackage.jar().orElseThrow().equals(
                                "io/quarkus/quarkus-custom-deployment/1.0.0/quarkus-custom-deployment-1.0.0-deployment.jar")));
    }

    @Test
    void quarkusDeploymentArtifactWithUnsupportedTypeFailsClearly() {
        addArtifact("io.quarkus", "quarkus-custom", "1.0.0", """
                <project>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-custom</artifactId>
                  <version>1.0.0</version>
                </project>
                """, Map.of(
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-custom-deployment::zip:1.0.0\n"));
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(
                        projectDir,
                        quarkusConfigWithDependencies(Map.of("io.quarkus:quarkus-custom", "1.0.0")),
                        cacheRoot));

        assertTrue(exception.getMessage().contains("declares deployment artifact"));
        assertTrue(exception.getMessage().contains("currently supports only jar deployment artifacts"));
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

    private ProjectConfig quarkusConfigWithDependencies(Map<String, String> dependencies) {
        return configWithDependencies(dependencies)
                .withFrameworkSettings(new FrameworkSettings(
                        new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));
    }

    private ProjectConfig quarkusPlatformConfigWithDependencies(Map<String, String> dependencies) {
        return new ProjectConfig(
                        new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                        Map.of("test", baseUri.toString()),
                        Map.of("io.quarkus.platform:quarkus-bom", "3.33.0"),
                        Map.of(),
                        dependencies.keySet(),
                        Map.of(),
                        Set.of(),
                        Map.of(),
                        Set.of(),
                        Map.of(),
                        Set.of(),
                        BuildSettings.defaults(),
                        null)
                .withFrameworkSettings(new FrameworkSettings(
                        new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));
    }

    private ProjectConfig configWithTestDependencies(Map<String, String> testDependencies) {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of(),
                testDependencies,
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

    private ProjectConfig runtimeProvidedConfig() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of("com.example:runtime-tool", "1.0.0"),
                Set.of(),
                Map.of("jakarta.servlet:jakarta.servlet-api", "6.1.0"),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null,
                null,
                null);
    }

    private ProjectConfig devConfig() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of("com.example:devtools", "1.0.0"),
                Set.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null,
                null,
                null);
    }

    private ProjectConfig springBootPlatformConfig() {
        return platformConfig().withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT));
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
        addArtifact(groupId, artifactId, version, pom, Map.of());
    }

    private void addArtifact(
            String groupId,
            String artifactId,
            String version,
            String pom,
            Map<String, String> jarEntries) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + ".pom", pom.getBytes(StandardCharsets.UTF_8));
        responses.put(base + ".jar", jarBytes(jarEntries));
    }

    private void addClassifierJar(
            String groupId,
            String artifactId,
            String version,
            String classifier,
            Map<String, String> jarEntries) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + "-" + classifier + ".jar", jarBytes(jarEntries));
    }

    private void addJUnitConsoleArtifact(String version) {
        addArtifact("org.junit.platform", "junit-platform-console", version, """
                <project>
                  <groupId>org.junit.platform</groupId>
                  <artifactId>junit-platform-console</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(version));
    }

    private void addPom(String groupId, String artifactId, String version, String pom) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + ".pom", pom.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] jarBytes(Map<String, String> entries) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (JarOutputStream jar = new JarOutputStream(bytes)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    jar.putNextEntry(new JarEntry(entry.getKey()));
                    jar.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    jar.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("Could not create test jar bytes.", exception);
        }
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
