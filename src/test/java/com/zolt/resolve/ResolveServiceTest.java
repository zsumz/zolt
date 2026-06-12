package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zolt.cache.ArtifactCacheException;
import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.LockPolicyEffect;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.BuildSettings;
import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyConstraintKind;
import com.zolt.project.DependencyExclusionSpec;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencyPolicyExclusion;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.OpenApiGenerationSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import com.zolt.toml.ZoltTomlParser;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final Set<String> slowPomPaths = ConcurrentHashMap.newKeySet();
    private final Set<String> slowArtifactPaths = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> responseDelayMillis = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final AtomicInteger totalRequests = new AtomicInteger();
    private final AtomicInteger activePomRequests = new AtomicInteger();
    private final AtomicInteger maxPomRequests = new AtomicInteger();
    private final AtomicInteger activeArtifactRequests = new AtomicInteger();
    private final AtomicInteger maxArtifactRequests = new AtomicInteger();

    @TempDir
    private Path tempDir;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
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
        serverExecutor.shutdownNow();
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
        assertTrue(result.metrics().pomDownloadNanos() > 0);
        assertTrue(result.metrics().artifactDownloadNanos() > 0);
        assertTrue(result.metrics().pomCacheHitNanos() > 0);
        assertEquals(0, result.metrics().artifactCacheHitNanos());
        assertTrue(result.metrics().rawPomParseNanos() > 0);
        assertTrue(result.metrics().effectivePomBuildNanos() > 0);
        assertTrue(result.metrics().graphTraversalNanos() > 0);
        assertTrue(result.metrics().lockfileAssemblyNanos() > 0);
        assertTrue(result.metrics().lockfileWriteNanos() > 0);
        assertEquals(0, result.metrics().lockfileVerificationNanos());
        assertTrue(Files.exists(result.lockfilePath()));

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.projectResolutionFingerprint().orElseThrow().startsWith("sha256:"));
        assertEquals(2, lockfile.packages().size());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app")) && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "lib")) && !lockPackage.direct()));
    }

    @Test
    void resolveTriesRepositoriesInStableOrderUntilArtifactIsFound() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                "empty" = "%s"
                "test" = "%s"

                [dependencies]
                "com.example:app" = "1.0.0"
                """.formatted(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/empty/",
                baseUri));

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);

        assertEquals(2, result.resolvedCount());
        assertEquals(1, requestCount("/empty/com/example/app/1.0.0/app-1.0.0.pom"));
        assertEquals(1, requestCount("/maven2/com/example/app/1.0.0/app-1.0.0.pom"));
    }

    @Test
    void mavenLocalOverlayTakesPrecedenceOverConfiguredRepositoriesAndRecordsSafeOrigin() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        Path mavenLocalRoot = tempDir.resolve("m2/repository");
        createDirectory(projectDir);
        writeLocalArtifact(mavenLocalRoot, "com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                </project>
                """, Map.of("local.txt", "local overlay wins\n"));

        ResolveResult result = resolveService.resolve(
                projectDir,
                config(),
                cacheRoot,
                false,
                new ResolveOptions(false, List.of(RepositoryOverlay.mavenLocal(mavenLocalRoot)), false));

        assertEquals(1, result.resolvedCount());
        assertEquals(0, requestCount("/maven2/com/example/app/1.0.0/app-1.0.0.pom"));
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage app = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "app")))
                .findFirst()
                .orElseThrow();
        assertEquals("local-overlay:maven-local", app.source());
        assertEquals(
                Optional.of("overlays/maven-local/com/example/app/1.0.0/app-1.0.0.jar"),
                app.jar());
        assertEquals(
                Optional.of("overlays/maven-local/com/example/app/1.0.0/app-1.0.0.pom"),
                app.pom());
        assertTrue(app.jar().orElseThrow().startsWith("overlays/maven-local/"));
        assertTrue(app.pom().orElseThrow().startsWith("overlays/maven-local/"));
        assertTrue(app.jar().orElseThrow().contains("app-1.0.0.jar"));
        assertTrue(app.jar().orElseThrow().indexOf(tempDir.toString()) < 0);
        assertTrue(Files.isRegularFile(cacheRoot.resolve(app.jar().orElseThrow())));
    }

    @Test
    void mavenLocalOverlayFallsBackToConfiguredRepositoriesWhenArtifactIsMissing() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        Path mavenLocalRoot = tempDir.resolve("empty-m2/repository");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                config(),
                cacheRoot,
                false,
                new ResolveOptions(false, List.of(RepositoryOverlay.mavenLocal(mavenLocalRoot)), false));

        assertEquals(2, result.resolvedCount());
        assertEquals(1, requestCount("/maven2/com/example/app/1.0.0/app-1.0.0.pom"));
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream()
                .allMatch(lockPackage -> "maven-central".equals(lockPackage.source())));
    }

    @Test
    void lockedResolveCanRejectExistingLocalOverlayOrigins() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        Path mavenLocalRoot = tempDir.resolve("m2/repository");
        createDirectory(projectDir);
        writeLocalArtifact(mavenLocalRoot, "com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                </project>
                """, Map.of());
        resolveService.resolve(
                projectDir,
                config(),
                cacheRoot,
                false,
                new ResolveOptions(false, List.of(RepositoryOverlay.mavenLocal(mavenLocalRoot)), false));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(
                        projectDir,
                        config(),
                        cacheRoot,
                        true,
                        new ResolveOptions(false, List.of(), true)));

        assertTrue(exception.getMessage().contains("Local repository overlay artifacts are not allowed"));
        assertTrue(exception.getMessage().contains("refresh zolt.lock from configured repositories"));
    }

    @Test
    void credentialedRepositoryFailsBeforeNetworkWhenEnvironmentIsMissing() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                "company" = { url = "%s", credentials = "company-artifactory" }

                [repositoryCredentials.company-artifactory]
                usernameEnv = "ZOLT_TEST_MISSING_REPOSITORY_USERNAME"
                passwordEnv = "ZOLT_TEST_MISSING_REPOSITORY_PASSWORD"

                [dependencies]
                "com.example:app" = "1.0.0"
                """.formatted(baseUri));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, config, cacheRoot));

        assertTrue(exception.getMessage().contains("Repository `company` requires credentials `company-artifactory`"));
        assertTrue(exception.getMessage().contains("ZOLT_TEST_MISSING_REPOSITORY_USERNAME"));
        assertTrue(exception.getMessage().contains("ZOLT_TEST_MISSING_REPOSITORY_PASSWORD"));
        assertTrue(exception.getMessage().contains("Secret values are never written to zolt.lock or command output."));
        assertEquals(0, totalRequests.get());
    }

    @Test
    void directDependencyMetadataExclusionsReachResolverRequests() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = config().withDependencyMetadata(Map.of(
                DependencyMetadata.key("dependencies", "com.example:app"),
                new DependencyMetadata(
                        "dependencies",
                        "com.example:app",
                        "1.0.0",
                        false,
                        null,
                        false,
                        false,
                        List.of(new DependencyExclusionSpec("com.example", "lib")))));

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());

        assertEquals(1, result.resolvedCount());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app"))));
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "lib"))));
        assertTrue(lockfile.policyEffects().stream().anyMatch(effect ->
                "edge-exclusion".equals(effect.kind())
                        && effect.packageId().equals(new PackageId("com.example", "lib"))
                        && effect.requestedVersion().orElseThrow().equals("1.0.0")
                        && effect.source().orElseThrow().equals("com.example:app:1.0.0")
                        && effect.policy().contains("dependency edge exclusion")));
    }

    @Test
    void directVersionRefDependencyRecordsLockfilePolicy() {
        Path projectDir = tempDir.resolve("project-version-ref");
        Path cacheRoot = tempDir.resolve("cache-version-ref");
        createDirectory(projectDir);
        ProjectConfig config = new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                test = "%s"

                [versions]
                app = "1.0.0"

                [dependencies]
                "com.example:app" = { versionRef = "app" }
                """.formatted(baseUri));

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage app = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "app")))
                .filter(LockPackage::direct)
                .findFirst()
                .orElseThrow();

        assertEquals("1.0.0", app.version());
        assertTrue(app.policies().contains(
                "version-ref: com.example:app -> 1.0.0 from [versions].app"));
    }

    @Test
    void dependencyPolicyExcludesMatchingTransitives() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = config().withDependencyPolicy(new DependencyPolicySettings(
                List.of(new DependencyPolicyExclusion(
                        "com.example",
                        "lib",
                        Optional.of("Use the internal logging bridge instead"))),
                Map.of()));

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());

        assertEquals(1, result.resolvedCount());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app"))));
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "lib"))));
        LockPolicyEffect effect = lockfile.policyEffects().stream()
                .filter(policyEffect -> policyEffect.packageId().equals(new PackageId("com.example", "lib")))
                .findFirst()
                .orElseThrow();
        assertEquals("global-exclusion", effect.kind());
        assertEquals("1.0.0", effect.requestedVersion().orElseThrow());
        assertEquals("com.example:app:1.0.0", effect.source().orElseThrow());
        assertEquals("[dependencyPolicy].exclude com.example:lib (Use the internal logging bridge instead)", effect.policy());
    }

    @Test
    void strictDependencyConstraintSelectsTransitiveVersionAndRecordsPolicy() {
        addArtifact("com.example", "lib", "2.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>lib</artifactId>
                  <version>2.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = config().withDependencyPolicy(new DependencyPolicySettings(
                List.of(),
                Map.of(
                        "com.example:lib",
                        new DependencyConstraint(
                                "com.example:lib",
                                "2.0.0",
                                DependencyConstraintKind.STRICT,
                                Optional.of("Enterprise baseline")))));

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage lib = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "lib")))
                .findFirst()
                .orElseThrow();

        assertEquals(2, result.resolvedCount());
        assertEquals("2.0.0", lib.version());
        assertEquals(
                List.of("strict-version: com.example:lib requested 1.0.0 -> 2.0.0 (Enterprise baseline)"),
                lib.policies());
        assertTrue(lockfile.policyEffects().stream().anyMatch(effect ->
                "strict-version".equals(effect.kind())
                        && effect.packageId().equals(new PackageId("com.example", "lib"))
                        && effect.requestedVersion().orElseThrow().equals("1.0.0")
                        && effect.source().orElseThrow().equals("com.example:app:1.0.0")
                        && effect.policy().equals("strict-version: com.example:lib requested 1.0.0 -> 2.0.0 (Enterprise baseline)")));
    }

    @Test
    void directDependencyWinsOverStrictTransitiveConstraint() {
        addArtifact("com.example", "lib", "2.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>lib</artifactId>
                  <version>2.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = configWithDependencies(Map.of(
                        "com.example:app", "1.0.0",
                        "com.example:lib", "1.0.0"))
                .withDependencyPolicy(new DependencyPolicySettings(
                        List.of(),
                        Map.of(
                                "com.example:lib",
                                new DependencyConstraint(
                                        "com.example:lib",
                                        "2.0.0",
                                        DependencyConstraintKind.STRICT,
                                        Optional.empty()))));

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage lib = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "lib")))
                .findFirst()
                .orElseThrow();

        assertEquals("1.0.0", lib.version());
        assertTrue(lib.direct());
        assertTrue(lib.policies().isEmpty());
        assertEquals(1, result.conflictCount());
    }

    @Test
    void dependencyPolicyRejectsExcludedDirectDependency() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = config().withDependencyPolicy(new DependencyPolicySettings(
                List.of(new DependencyPolicyExclusion(
                        "com.example",
                        "app",
                        Optional.of("Application artifact is banned in this policy"))),
                Map.of()));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, config, cacheRoot));

        assertTrue(exception.getMessage().contains("Dependency policy excludes direct dependency `com.example:app`"));
        assertTrue(exception.getMessage().contains("Application artifact is banned in this policy"));
        assertTrue(exception.getMessage().contains("Remove the direct dependency or remove the matching [dependencyPolicy].exclude entry"));
        assertEquals(0, totalRequests.get());
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
        assertEquals(4, second.metrics().pomCacheHits() + second.metrics().jarCacheHits());
        assertTrue(first.metrics().pomDownloadNanos() > 0);
        assertTrue(first.metrics().artifactDownloadNanos() > 0);
        assertTrue(second.metrics().pomCacheHitNanos() > 0);
        assertTrue(second.metrics().artifactCacheHitNanos() > 0);
        assertEquals(0, second.metrics().pomDownloadNanos() + second.metrics().artifactDownloadNanos());
        assertTrue(second.metrics().rawPomParseNanos() > 0);
        assertTrue(second.metrics().effectivePomBuildNanos() > 0);
    }

    @Test
    void selectedArtifactsAreMaterializedConcurrentlyAfterGraphSelection() throws IOException {
        addArtifact("com.example", "alpha", "1.0.0", simplePom("com.example", "alpha", "1.0.0"));
        addArtifact("com.example", "beta", "1.0.0", simplePom("com.example", "beta", "1.0.0"));
        addArtifact("com.example", "gamma", "1.0.0", simplePom("com.example", "gamma", "1.0.0"));
        slowArtifactPaths.add(jarRepositoryPath("com.example", "alpha", "1.0.0"));
        slowArtifactPaths.add(jarRepositoryPath("com.example", "beta", "1.0.0"));
        slowArtifactPaths.add(jarRepositoryPath("com.example", "gamma", "1.0.0"));
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        Map<String, String> dependencies = new java.util.LinkedHashMap<>();
        dependencies.put("com.example:alpha", "1.0.0");
        dependencies.put("com.example:beta", "1.0.0");
        dependencies.put("com.example:gamma", "1.0.0");
        ProjectConfig config = configWithDependencies(dependencies);

        ResolveResult result = resolveService.resolve(
                projectDir,
                config,
                cacheRoot);

        assertEquals(3, result.resolvedCount());
        assertTrue(maxArtifactRequests.get() > 1);
        assertTrue(maxArtifactRequests.get() <= 4);

        Path secondProjectDir = tempDir.resolve("project-second");
        Path secondCacheRoot = tempDir.resolve("cache-second");
        createDirectory(secondProjectDir);
        ResolveResult second = resolveService.resolve(secondProjectDir, config, secondCacheRoot);

        assertEquals(Files.readString(result.lockfilePath()), Files.readString(second.lockfilePath()));
    }

    @Test
    void pomFrontierMetadataIsFetchedConcurrentlyWithStableLockfile() throws IOException {
        addArtifact("com.example", "frontier-root", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>frontier-root</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>alpha</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>beta</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>gamma</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "alpha", "1.0.0", simplePom("com.example", "alpha", "1.0.0"));
        addArtifact("com.example", "beta", "1.0.0", simplePom("com.example", "beta", "1.0.0"));
        addArtifact("com.example", "gamma", "1.0.0", simplePom("com.example", "gamma", "1.0.0"));
        slowPomPaths.add(pomRepositoryPath("com.example", "alpha", "1.0.0"));
        slowPomPaths.add(pomRepositoryPath("com.example", "beta", "1.0.0"));
        slowPomPaths.add(pomRepositoryPath("com.example", "gamma", "1.0.0"));
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = configWithDependencies(Map.of("com.example:frontier-root", "1.0.0"));

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);

        assertEquals(4, result.resolvedCount());
        assertTrue(maxPomRequests.get() > 1);
        assertTrue(maxPomRequests.get() <= 4);

        Path secondProjectDir = tempDir.resolve("project-second");
        Path secondCacheRoot = tempDir.resolve("cache-second");
        createDirectory(secondProjectDir);
        ResolveResult second = resolveService.resolve(secondProjectDir, config, secondCacheRoot);

        assertEquals(Files.readString(result.lockfilePath()), Files.readString(second.lockfilePath()));
    }

    @Test
    void randomizedRepositoryResponseTimingKeepsLockfileStable() throws IOException {
        Map<String, String> dependencies = new java.util.LinkedHashMap<>();
        dependencies.put("com.example:alpha", "1.0.0");
        dependencies.put("com.example:beta", "1.0.0");
        dependencies.put("com.example:gamma", "1.0.0");
        dependencies.put("com.example:delta", "1.0.0");
        dependencies.put("com.example:epsilon", "1.0.0");
        dependencies.keySet().forEach(coordinate -> {
            String artifactId = coordinate.substring(coordinate.indexOf(':') + 1);
            addArtifact("com.example", artifactId, "1.0.0", simplePom("com.example", artifactId, "1.0.0"));
        });
        setResponseDelays(Map.of(
                "alpha", 120L,
                "beta", 10L,
                "gamma", 70L,
                "delta", 30L,
                "epsilon", 90L));
        Path projectDir = tempDir.resolve("project-randomized");
        Path cacheRoot = tempDir.resolve("cache-randomized");
        createDirectory(projectDir);
        ProjectConfig config = configWithDependencies(dependencies);

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);

        responseDelayMillis.clear();
        setResponseDelays(Map.of(
                "alpha", 5L,
                "beta", 130L,
                "gamma", 25L,
                "delta", 100L,
                "epsilon", 45L));
        Path secondProjectDir = tempDir.resolve("project-randomized-second");
        Path secondCacheRoot = tempDir.resolve("cache-randomized-second");
        createDirectory(secondProjectDir);
        ResolveResult second = resolveService.resolve(secondProjectDir, config, secondCacheRoot);

        assertEquals(Files.readString(result.lockfilePath()), Files.readString(second.lockfilePath()));
    }

    @Test
    void parallelArtifactDownloadFailuresAreReportedInSortedOrder() {
        addPom("com.example", "zeta-missing", "1.0.0", simplePom("com.example", "zeta-missing", "1.0.0"));
        addPom("com.example", "alpha-missing", "1.0.0", simplePom("com.example", "alpha-missing", "1.0.0"));
        Path projectDir = tempDir.resolve("project-missing-artifacts");
        Path cacheRoot = tempDir.resolve("cache-missing-artifacts");
        createDirectory(projectDir);
        Map<String, String> dependencies = new java.util.LinkedHashMap<>();
        dependencies.put("com.example:zeta-missing", "1.0.0");
        dependencies.put("com.example:alpha-missing", "1.0.0");

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, configWithDependencies(dependencies), cacheRoot));

        assertTrue(exception.getMessage().contains("Selected artifact downloads failed:"));
        assertTrue(exception.getMessage().indexOf("com.example:alpha-missing:1.0.0")
                < exception.getMessage().indexOf("com.example:zeta-missing:1.0.0"));
        assertTrue(exception.getMessage().contains("Retry the command or check your repository and network settings."));
    }

    @Test
    void repeatedParentPomsAreParsedOncePerResolve() {
        addPom("com.example", "parent", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        addArtifact("com.example", "child-a", "1.0.0", """
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>child-a</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        addArtifact("com.example", "child-b", "1.0.0", """
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>child-b</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                configWithDependencies(Map.of(
                        "com.example:child-a", "1.0.0",
                        "com.example:child-b", "1.0.0")),
                cacheRoot);

        assertEquals(2, result.resolvedCount());
        assertEquals(1, result.metrics().rawPomCacheHits());
        assertTrue(result.metrics().rawPomCacheMisses() >= 3);
        assertTrue(Files.exists(result.lockfilePath()));
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
        resetRequestCounts();

        ResolveResult result = resolveService.resolve(projectDir, config(), cacheRoot, false, true);

        assertEquals(2, result.resolvedCount());
        assertEquals(0, result.downloadCount());
        assertEquals(0, totalRequests.get());
        assertTrue(requestCounts.isEmpty());
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
        LockPackage app = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "app")))
                .findFirst()
                .orElseThrow();
        assertEquals("1.0.0", app.version());
        assertTrue(app.direct());
        assertEquals(
                List.of("managed-version: com.example:app -> 1.0.0 from com.example:platform:1.0.0"),
                app.policies());
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "platform"))));
    }

    @Test
    void lockedResolveFailsWhenPlatformVersionRefEdgeChangesWithoutConcreteVersionChange() throws IOException {
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
        Path projectDir = tempDir.resolve("project-platform-alias-lock");
        Path cacheRoot = tempDir.resolve("cache-platform-alias-lock");
        createDirectory(projectDir);
        resolveService.resolve(projectDir, platformVersionRefConfig("platform-one"), cacheRoot);
        String existingLockfile = Files.readString(projectDir.resolve("zolt.lock"));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, platformVersionRefConfig("platform-two"), cacheRoot, true));

        assertTrue(existingLockfile.contains("aliasFingerprint = \"sha256:"));
        assertTrue(exception.getMessage().contains("zolt.lock is out of date"));
        assertEquals(existingLockfile, Files.readString(projectDir.resolve("zolt.lock")));
    }

    @Test
    void lockedResolveFailsWhenRepositoryInputChangesWithoutGraphChange() throws IOException {
        Path projectDir = tempDir.resolve("project-repository-lock");
        Path cacheRoot = tempDir.resolve("cache-repository-lock");
        createDirectory(projectDir);
        resolveService.resolve(projectDir, config(), cacheRoot);
        String existingLockfile = Files.readString(projectDir.resolve("zolt.lock"));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, configWithRepository(baseUri + "?changed=true"), cacheRoot, true));

        assertTrue(existingLockfile.contains("projectResolutionFingerprint = \"sha256:"));
        assertTrue(exception.getMessage().contains("zolt.lock is out of date"));
        assertEquals(existingLockfile, Files.readString(projectDir.resolve("zolt.lock")));
    }

    @Test
    void laterProjectPlatformManagedVersionRecordsSelectedPlatformSource() {
        addPom("com.example", "platform-a", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>platform-a</artifactId>
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
        addPom("com.example", "platform-b", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>platform-b</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>app</artifactId>
                        <version>2.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addArtifact("com.example", "app", "2.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>2.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of(
                        "com.example:platform-b", "1.0.0",
                        "com.example:platform-a", "1.0.0"),
                Map.of(),
                Set.of("com.example:app"),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null);

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage app = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "app")))
                .findFirst()
                .orElseThrow();
        assertEquals(1, result.resolvedCount());
        assertEquals("2.0.0", app.version());
        assertEquals(
                List.of("managed-version: com.example:app -> 2.0.0 from com.example:platform-b:1.0.0"),
                app.policies());
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
    void springBootWarPackageModeAddsLoaderFromProjectPlatform() {
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

        ResolveResult result = resolveService.resolve(projectDir, springBootWarPlatformConfig(), cacheRoot);

        assertEquals(3, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.springframework.boot", "spring-boot-loader"))
                        && lockPackage.version().equals("4.0.6")
                        && lockPackage.scope() == DependencyScope.RUNTIME
                        && !lockPackage.direct()));
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
    void springBootWarPackageModeFailsClearlyWithoutManagedLoaderVersion() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(
                        projectDir,
                        config().withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT_WAR)),
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
    void coverageResolveAddsJacocoToolingOnlyScope() {
        addArtifact("com.example", "app", "1.0.0", simplePom("com.example", "app", "1.0.0"));
        addJUnitConsoleArtifact("1.11.4");
        addArtifact("org.jacoco", "org.jacoco.agent", "0.8.14", """
                <project>
                  <groupId>org.jacoco</groupId>
                  <artifactId>org.jacoco.agent</artifactId>
                  <version>0.8.14</version>
                </project>
                """);
        addClassifierJar("org.jacoco", "org.jacoco.agent", "0.8.14", "runtime", Map.of());
        addArtifact("org.jacoco", "org.jacoco.cli", "0.8.14", """
                <project>
                  <groupId>org.jacoco</groupId>
                  <artifactId>org.jacoco.cli</artifactId>
                  <version>0.8.14</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolveWithCoverageTooling(
                projectDir,
                configWithTestDependencies(Map.of("com.example:app", "1.0.0")),
                cacheRoot);

        assertEquals(4, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.jacoco", "org.jacoco.agent"))
                        && lockPackage.version().equals("0.8.14")
                        && lockPackage.scope() == DependencyScope.TOOL_COVERAGE
                        && lockPackage.jar().orElseThrow().endsWith("org.jacoco.agent-0.8.14-runtime.jar")));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.jacoco", "org.jacoco.cli"))
                        && lockPackage.version().equals("0.8.14")
                        && lockPackage.scope() == DependencyScope.TOOL_COVERAGE));
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
    void directTestDependencyRelocationLocksRelocatedArtifact() {
        addPom("io.quarkus", "quarkus-junit5", "3.33.2", """
                <project>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-junit5</artifactId>
                  <version>3.33.2</version>
                  <distributionManagement>
                    <relocation>
                      <groupId>io.quarkus</groupId>
                      <artifactId>quarkus-junit</artifactId>
                      <version>${project.version}</version>
                      <message>Use io.quarkus:quarkus-junit instead.</message>
                    </relocation>
                  </distributionManagement>
                </project>
                """);
        addArtifact("io.quarkus", "quarkus-junit", "3.33.2", """
                <project>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-junit</artifactId>
                  <version>3.33.2</version>
                </project>
                """);
        addJUnitConsoleArtifact("1.11.4");
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                configWithTestDependencies(Map.of("io.quarkus:quarkus-junit5", "3.33.2")),
                cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("io.quarkus", "quarkus-junit"))
                        && lockPackage.version().equals("3.33.2")
                        && lockPackage.scope() == DependencyScope.TEST
                        && lockPackage.direct()
                        && lockPackage.jar().orElseThrow().equals(
                                "io/quarkus/quarkus-junit/3.33.2/quarkus-junit-3.33.2.jar")));
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("io.quarkus", "quarkus-junit5"))));
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
    void openApiToolResolvesToToolScopeOnly() {
        addArtifact("org.openapitools", "openapi-generator-cli", "7.11.0", """
                <project>
                  <groupId>org.openapitools</groupId>
                  <artifactId>openapi-generator-cli</artifactId>
                  <version>7.11.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>generator-helper</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "generator-helper", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>generator-helper</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, openApiConfig(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.openapitools", "openapi-generator-cli"))
                        && lockPackage.scope() == DependencyScope.TOOL_OPENAPI
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "generator-helper"))
                        && lockPackage.scope() == DependencyScope.TOOL_OPENAPI
                        && !lockPackage.direct()));

        ClasspathSet classpaths = new ClasspathBuilder().build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        assertEquals(List.of(), classpaths.compile().entries());
        assertEquals(List.of(), classpaths.runtime().entries());
        assertEquals(List.of(), classpaths.test().entries());
        assertEquals(List.of(), classpaths.processor().entries());
        assertEquals(List.of(), classpaths.testProcessor().entries());
        assertEquals(List.of(), classpaths.quarkusDeployment().entries());
    }

    @Test
    void lockedResolveFailsWhenOpenApiToolVersionRefEdgeChangesWithoutConcreteVersionChange() throws IOException {
        addArtifact("org.openapitools", "openapi-generator-cli", "7.11.0", """
                <project>
                  <groupId>org.openapitools</groupId>
                  <artifactId>openapi-generator-cli</artifactId>
                  <version>7.11.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project-openapi-tool-alias-lock");
        Path cacheRoot = tempDir.resolve("cache-openapi-tool-alias-lock");
        createDirectory(projectDir);
        resolveService.resolve(projectDir, openApiVersionRefConfig("openapi-one"), cacheRoot);
        String existingLockfile = Files.readString(projectDir.resolve("zolt.lock"));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, openApiVersionRefConfig("openapi-two"), cacheRoot, true));

        assertTrue(existingLockfile.contains("aliasFingerprint = \"sha256:"));
        assertTrue(exception.getMessage().contains("zolt.lock is out of date"));
        assertEquals(existingLockfile, Files.readString(projectDir.resolve("zolt.lock")));
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
    void quarkusPlatformPropertiesArtifactIsResolvedFromPlatformBom() {
        addPom("io.quarkus.platform", "quarkus-bom", "3.33.0", """
                <project>
                  <groupId>io.quarkus.platform</groupId>
                  <artifactId>quarkus-bom</artifactId>
                  <version>3.33.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>io.quarkus.platform</groupId>
                        <artifactId>quarkus-bom-quarkus-platform-properties</artifactId>
                        <version>3.33.0</version>
                        <type>properties</type>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addArtifact(
                "io.quarkus.platform",
                "quarkus-bom-quarkus-platform-properties",
                "3.33.0",
                "properties",
                """
                platform.quarkus.native.builder-image=builder
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                quarkusPlatformConfigWithDependencies(Map.of()),
                cacheRoot);

        assertEquals(1, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage properties = lockfile.packages().getFirst();
        assertEquals(
                new PackageId("io.quarkus.platform", "quarkus-bom-quarkus-platform-properties"),
                properties.packageId());
        assertEquals(DependencyScope.QUARKUS_DEPLOYMENT, properties.scope());
        assertTrue(properties.jar().isEmpty());
        assertEquals("properties", properties.artifactType().orElseThrow());
        assertEquals(
                "io/quarkus/platform/quarkus-bom-quarkus-platform-properties/3.33.0/quarkus-bom-quarkus-platform-properties-3.33.0.properties",
                properties.artifact().orElseThrow());
        assertEquals(List.of(), new ClasspathBuilder()
                .build(lockfileReader.classpathPackages(lockfile, cacheRoot))
                .quarkusDeployment()
                .entries());
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
        return configWithRepositoryAndDependencies(baseUri.toString(), dependencies);
    }

    private ProjectConfig configWithRepository(String repositoryUrl) {
        return configWithRepositoryAndDependencies(repositoryUrl, Map.of("com.example:app", "1.0.0"));
    }

    private ProjectConfig configWithRepositoryAndDependencies(String repositoryUrl, Map<String, String> dependencies) {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", repositoryUrl),
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

    private ProjectConfig platformVersionRefConfig(String alias) {
        return new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                test = "%s"

                [versions]
                "%s" = "1.0.0"

                [platforms]
                "com.example:platform" = { versionRef = "%s" }

                [dependencies]
                "com.example:app" = {}
                """.formatted(baseUri, alias, alias));
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

    private ProjectConfig springBootWarPlatformConfig() {
        return platformConfig().withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT_WAR));
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

    private ProjectConfig openApiConfig() {
        OpenApiGenerationSettings settings = new OpenApiGenerationSettings(
                Optional.of("org.openapitools:openapi-generator-cli"),
                Optional.of("7.11.0"),
                Optional.empty(),
                Optional.of("spring"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of());
        GeneratedSourceStep step = new GeneratedSourceStep(
                "public-api",
                GeneratedSourceKind.OPENAPI,
                "java",
                "target/generated/sources/openapi/public-api",
                List.of("src/main/openapi/public-api.yaml"),
                true,
                true,
                settings);
        return configWithDependencies(Map.of())
                .withBuildSettings(BuildSettings.defaults().withGeneratedSources(List.of(step), List.of()));
    }

    private ProjectConfig openApiVersionRefConfig(String alias) {
        return new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                test = "%s"

                [versions]
                "%s" = "7.11.0"

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                versionRef = "%s"

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                generator = "spring"
                """.formatted(baseUri, alias, alias));
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

    private static String simplePom(String groupId, String artifactId, String version) {
        return """
                <project>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(groupId, artifactId, version);
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

    private static String jarRepositoryPath(String groupId, String artifactId, String version) {
        return "/maven2/"
                + groupId.replace('.', '/')
                + "/"
                + artifactId
                + "/"
                + version
                + "/"
                + artifactId
                + "-"
                + version
                + ".jar";
    }

    private static String pomRepositoryPath(String groupId, String artifactId, String version) {
        return "/maven2/"
                + groupId.replace('.', '/')
                + "/"
                + artifactId
                + "/"
                + version
                + "/"
                + artifactId
                + "-"
                + version
                + ".pom";
    }

    private void setResponseDelays(Map<String, Long> artifactDelaysMillis) {
        artifactDelaysMillis.forEach((artifactId, delayMillis) -> {
            responseDelayMillis.put(pomRepositoryPath("com.example", artifactId, "1.0.0"), delayMillis);
            responseDelayMillis.put(jarRepositoryPath("com.example", artifactId, "1.0.0"), delayMillis);
        });
    }

    private void resetRequestCounts() {
        requestCounts.clear();
        totalRequests.set(0);
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

    private void addArtifact(
            String groupId,
            String artifactId,
            String version,
            String extension,
            String content) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + ".pom", """
                <project>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(groupId, artifactId, version).getBytes(StandardCharsets.UTF_8));
        responses.put(base + "." + extension, content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeLocalArtifact(
            Path root,
            String groupId,
            String artifactId,
            String version,
            String pom,
            Map<String, String> jarEntries) {
        String base = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        writeFile(root.resolve(base + ".pom"), pom.getBytes(StandardCharsets.UTF_8));
        writeFile(root.resolve(base + ".jar"), jarBytes(jarEntries));
    }

    private static void writeFile(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new AssertionError("Could not write test file " + path, exception);
        }
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
        String path = exchange.getRequestURI().getPath();
        requestCounts.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
        totalRequests.incrementAndGet();
        if (slowPomPaths.contains(path)) {
            int active = activePomRequests.incrementAndGet();
            maxPomRequests.accumulateAndGet(active, Math::max);
            try {
                sleepServing(150L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while serving slow test POM.", exception);
            } finally {
                activePomRequests.decrementAndGet();
            }
        }
        if (slowArtifactPaths.contains(path)) {
            int active = activeArtifactRequests.incrementAndGet();
            maxArtifactRequests.accumulateAndGet(active, Math::max);
            try {
                sleepServing(150L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while serving slow test artifact.", exception);
            } finally {
                activeArtifactRequests.decrementAndGet();
            }
        }
        Long delayMillis = responseDelayMillis.get(path);
        if (delayMillis != null && delayMillis > 0) {
            try {
                sleepServing(delayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while serving delayed test response.", exception);
            }
        }
        byte[] body = responses.get(path);
        if (body == null) {
            respond(exchange, 404, "missing".getBytes(StandardCharsets.UTF_8));
            return;
        }
        respond(exchange, 200, body);
    }

    private int requestCount(String path) {
        return requestCounts.getOrDefault(path, new AtomicInteger()).get();
    }

    private static void sleepServing(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private static void respond(HttpExchange exchange, int statusCode, byte[] body) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
