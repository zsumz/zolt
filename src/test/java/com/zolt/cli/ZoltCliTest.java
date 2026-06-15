package com.zolt.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class ZoltCliTest {
    @TempDir
    private Path tempDir;

    @Test
    void checkContextCiRequireOfflineReadyPassesWithSeededCache() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("check-context-ci-offline-ready-ok");
            Path cacheRoot = tempDir.resolve("cache-offline-ready-ok");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"), Map.of());
            CommandResult resolve = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString());

            CommandResult result = execute(
                    "check",
                    "--context", "ci",
                    "--require-offline-ready",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString());

            assertEquals(0, resolve.exitCode());
            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("ok lockfile zolt.lock zolt.lock matches zolt.toml and locked artifacts are available from the local cache."));
            assertEquals("", result.stderr());
        }
    }

    @Test
    void checkContextCiRequireOfflineReadyReportsMissingCache() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("check-context-ci-offline-ready-missing-cache");
            Path seededCache = tempDir.resolve("cache-offline-ready-seeded");
            Path emptyCache = tempDir.resolve("cache-offline-ready-empty");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"), Map.of());
            CommandResult resolve = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", seededCache.toString());

            CommandResult result = execute(
                    "check",
                    "--context", "ci",
                    "--require-offline-ready",
                    "--cwd", projectDir.toString(),
                    "--cache-root", emptyCache.toString());

            assertEquals(0, resolve.exitCode());
            assertEquals(1, result.exitCode());
            assertTrue(result.stdout().contains("error lockfile zolt.lock Offline mode requires cached POM"));
            assertTrue(result.stdout().contains("next: Run `zolt resolve` to seed the cache, then retry `zolt check --context ci --require-offline-ready`."));
            assertEquals("", result.stderr());
        }
    }

    @Test
    void checkRefusesArbitraryHookNames() throws IOException {
        Path projectDir = tempDir.resolve("check-hook");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-hook"));

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "mvn verify");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error unsupported-check mvn verify Unsupported quality check `mvn verify`."));
        assertTrue(result.stdout().contains("Zolt does not run Maven goals, Gradle tasks, shell commands, or arbitrary hooks."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkReportsMalformedConfigAsFailedCheck() throws IOException {
        Path projectDir = tempDir.resolve("check-malformed");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "check-malformed"
                version = "0.1.0"
                group = "com.example"
                java = "%s"

                [check]
                command = "mvn verify"
                """.formatted(currentJavaMajorVersion()));

        CommandResult result = execute("check", "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error command-surface zolt.toml Unknown top-level section [check] in zolt.toml."));
        assertTrue(result.stdout().contains("next: Fix zolt.toml, then run `zolt check` again."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkWorkspaceUsesMemberSelectionModel() throws IOException {
        WorkspaceCommandFixture.WorkspaceApplicationFixture fixture =
                WorkspaceCommandFixture.create(tempDir, "check-workspace");

        CommandResult result = execute(
                "check",
                "--workspace",
                "--member", "apps/api",
                "--cwd", fixture.workspaceDir().toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok command-surface check-workspace zolt check selected 2 workspace members"));
        assertTrue(result.stdout().contains("no Maven, Gradle, or shell hooks are run."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkWorkspaceProjectModelReportsUnusedVersionAliasesByMember() throws IOException {
        Path workspaceDir = tempDir.resolve("check-workspace-unused-version-alias");
        Path apiDir = workspaceDir.resolve("api");
        Files.createDirectories(apiDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "check-workspace-unused-version-alias"
                members = ["api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """

                [versions]
                boot = "4.0.6"
                lombok = "1.18.36"
                openapi = "7.11.0"
                test-lombok = "1.18.36"
                tomcat = "10.1.40"
                used = "1.0.0"
                unused = "2.0.0"

                [platforms]
                "org.springframework.boot:spring-boot-dependencies" = { versionRef = "boot" }

                [dependencies]
                "org.example:lib" = { versionRef = "used" }

                [annotationProcessors]
                "org.projectlombok:lombok" = { versionRef = "lombok" }

                [test.annotationProcessors]
                "org.projectlombok:lombok" = { versionRef = "test-lombok" }

                [dependencyConstraints]
                "org.apache.tomcat.embed:tomcat-embed-core" = { versionRef = "tomcat", kind = "strict" }

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                versionRef = "openapi"

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                generator = "spring"
                """);

        CommandResult result = execute(
                "check",
                "--workspace",
                "--cwd", workspaceDir.toString(),
                "--check", "project-model");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("skip project-model api [versions].unused Version alias `unused` is declared but not referenced by any versionRef."));
        assertFalse(result.stdout().contains("[versions].used"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkProjectModelReportsInvalidProjectPaths() throws IOException {
        Path projectDir = tempDir.resolve("check-invalid-model");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-invalid-model") + """

                [build]
                source = "/tmp/source"
                """);

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "project-model");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error project-model [build].source Path `/tmp/source` must be project-relative"));
        assertTrue(result.stdout().contains("next: Edit zolt.toml to use a relative path"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkProjectModelJsonReportsCompilerReleaseFailures() throws IOException {
        Path projectDir = tempDir.resolve("check-release-model");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-release-model") + """

                [compiler]
                release = "99"
                """);

        CommandResult result = execute(
                "check",
                "--format", "json",
                "--cwd", projectDir.toString(),
                "--check", "project-model");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("\"status\":\"error\""));
        assertTrue(result.stdout().contains("\"id\":\"project-model\""));
        assertTrue(result.stdout().contains("\"subject\":\"[compiler].release\""));
        assertTrue(result.stdout().contains("Compiler release `99` is newer than [project].java"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkProjectModelJsonReportsUnusedVersionAliases() throws IOException {
        Path projectDir = tempDir.resolve("check-unused-version-alias");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-unused-version-alias") + """

                [versions]
                boot = "4.0.6"
                lombok = "1.18.36"
                test-lombok = "1.18.36"
                tomcat = "10.1.40"
                used = "1.0.0"
                unused = "2.0.0"

                [platforms]
                "org.springframework.boot:spring-boot-dependencies" = { versionRef = "boot" }

                [dependencies]
                "org.example:lib" = { versionRef = "used" }

                [annotationProcessors]
                "org.projectlombok:lombok" = { versionRef = "lombok" }

                [test.annotationProcessors]
                "org.projectlombok:lombok" = { versionRef = "test-lombok" }

                [dependencyConstraints]
                "org.apache.tomcat.embed:tomcat-embed-core" = { versionRef = "tomcat", kind = "strict" }
                """);

        CommandResult result = execute(
                "check",
                "--format", "json",
                "--cwd", projectDir.toString(),
                "--check", "project-model");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"status\":\"ok\""));
        assertTrue(result.stdout().contains("\"subject\":\"[versions].unused\""));
        assertTrue(result.stdout().contains("Version alias `unused` is declared but not referenced by any versionRef."));
        assertTrue(result.stdout().contains("\"status\":\"skipped\""));
        assertFalse(result.stdout().contains("\"subject\":\"[versions].boot\""));
        assertFalse(result.stdout().contains("\"subject\":\"[versions].lombok\""));
        assertFalse(result.stdout().contains("\"subject\":\"[versions].openapi\""));
        assertFalse(result.stdout().contains("\"subject\":\"[versions].test-lombok\""));
        assertFalse(result.stdout().contains("\"subject\":\"[versions].tomcat\""));
        assertFalse(result.stdout().contains("\"subject\":\"[versions].used\""));
        assertEquals("", result.stderr());
    }

    @Test
    void checkDependencyMetadataPassesForOptionalPublishOnlyAndExclusions() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("org.example", "lib", "1.0.0", """
                    <project>
                      <groupId>org.example</groupId>
                      <artifactId>lib</artifactId>
                      <version>1.0.0</version>
                      <dependencies>
                        <dependency>
                          <groupId>org.example</groupId>
                          <artifactId>excluded</artifactId>
                          <version>1.0.0</version>
                        </dependency>
                        <dependency>
                          <groupId>org.example</groupId>
                          <artifactId>kept</artifactId>
                          <version>1.0.0</version>
                        </dependency>
                      </dependencies>
                    </project>
                    """);
            repository.addArtifact("org.example", "kept", "1.0.0", pom("org.example", "kept", "1.0.0"));
            Path projectDir = tempDir.resolve("check-dependency-metadata");
            Files.createDirectories(projectDir);
            Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-dependency-metadata") + """

                    [repositories]
                    test = "%s"

                    [dependencies]
                    "org.example:lib" = { version = "1.0.0", optional = true, exclusions = [{ group = "org.example", artifact = "excluded" }] }
                    "org.example:publish-only" = { version = "1.0.0", publishOnly = true }
                    """.formatted(repository.baseUri()));
            Path cacheRoot = tempDir.resolve("cache");
            CommandResult resolve = execute("resolve", "--cwd", projectDir.toString(), "--cache-root", cacheRoot.toString());
            assertEquals(0, resolve.exitCode());

            CommandResult result = execute(
                    "check",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString(),
                    "--check", "dependency-metadata");

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("ok dependency-metadata org.example:lib Dependency metadata for `org.example:lib` is represented in zolt.lock."));
            assertTrue(result.stdout().contains("ok dependency-metadata org.example:publish-only Publish-only dependency `org.example:publish-only` is kept out of zolt.lock classpaths."));
            assertEquals("", result.stderr());
        }
    }

    @Test
    void checkDependencyMetadataReportsPublishOnlyLeakage() throws IOException {
        Path projectDir = tempDir.resolve("check-publish-only-leak");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-publish-only-leak") + """

                [dependencies]
                "org.example:publish-only" = { version = "1.0.0", publishOnly = true }
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.example:publish-only"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                dependencies = []
                """);

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "dependency-metadata");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error dependency-metadata org.example:publish-only Publish-only dependency `org.example:publish-only` is present in zolt.lock."));
        assertTrue(result.stdout().contains("next: Run `zolt resolve`; if it remains, remove publishOnly = true"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkDependencyMetadataReportsExcludedDependencyOnDirectEdge() throws IOException {
        Path projectDir = tempDir.resolve("check-exclusion-leak");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-exclusion-leak") + """

                [dependencies]
                "org.example:lib" = { version = "1.0.0", exclusions = [{ group = "org.example", artifact = "excluded" }] }
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.example:lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                dependencies = ["org.example:excluded"]
                """);

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "dependency-metadata");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error dependency-metadata org.example:lib Excluded dependency `org.example:excluded` is still present"));
        assertTrue(result.stdout().contains("next: Check [dependencies].org.example:lib.exclusions and run `zolt resolve`."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkWorkspaceDependencyMetadataValidatesExportedApiEdges() throws IOException {
        Path workspaceDir = tempDir.resolve("check-workspace-dependency-metadata");
        Path apiDir = workspaceDir.resolve("api");
        Path bindingDir = workspaceDir.resolve("binding");
        Files.createDirectories(apiDir);
        Files.createDirectories(bindingDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "check-workspace-dependency-metadata"
                members = ["api", "binding"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));
        Files.writeString(bindingDir.resolve("zolt.toml"), memberConfig("binding") + """

                [api.dependencies]
                "com.example:api" = { workspace = "api" }
                """);
        Path cacheRoot = tempDir.resolve("cache");
        CommandResult resolve = execute(
                "resolve",
                "--workspace",
                "--cwd", workspaceDir.toString(),
                "--cache-root", cacheRoot.toString());
        assertEquals(0, resolve.exitCode());

        CommandResult result = execute(
                "check",
                "--workspace",
                "--cwd", workspaceDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "--check", "dependency-metadata");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok dependency-metadata binding com.example:api Workspace API dependency `com.example:api` is exported through zolt.lock."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkPrintsTimingsWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("check-timings");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-timings"));

        CommandResult result = execute("check", "--timings", "--timings-format", "json", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok command-surface check-timings"));
        assertTrue(result.stderr().contains("\"command\":\"check\""));
        assertTrue(result.stderr().contains("\"phase\":\"run quality checks\""));
        assertTrue(result.stderr().contains("\"checks\":\"1\""));
        assertTrue(result.stderr().contains("\"passed\":\"1\""));
    }

    @Test
    void packageReportsConfigErrorsCleanly() {
        CommandResult result = execute("package", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.toml"));
    }

    @Test
    void initCreatesProjectAndPrintsNextCommand() {
        CommandResult result = execute("init", "--cwd", tempDir.toString(), "hello");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Created Zolt project at"));
        assertTrue(result.stdout().contains("Next: cd hello"));
        assertTrue(Files.exists(tempDir.resolve("hello/zolt.toml")));
    }

    @Test
    void resolveReportsConfigErrorsCleanly() {
        CommandResult result = execute("resolve", "--cwd", tempDir.toString(), "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.toml"));
    }

    @Test
    void resolveReadsConfigWritesLockfileAndPrintsSummary() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            Files.createDirectories(projectDir);
            Files.writeString(projectDir.resolve("zolt.toml"), """
                    [project]
                    name = "demo"
                    version = "0.1.0"
                    group = "com.example"
                    java = "21"
                    main = "com.example.Main"

                    [repositories]
                    test = "%s"

                    [dependencies]
                    "com.example:app" = "1.0.0"
                    """.formatted(repository.baseUri()));

            CommandResult result = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Resolved 1 packages"));
            assertTrue(result.stdout().contains("Downloaded 2 artifacts"));
            assertTrue(result.stdout().contains("Conflicts 0"));
            assertTrue(result.stdout().contains("Wrote " + projectDir.resolve("zolt.lock")));
            assertEquals("", result.stderr());
            assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
        }
    }

    @Test
    void resolvePrintsTextTimingsWhenRequested() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"), Map.of());

            CommandResult result = execute(
                    "resolve",
                    "--timings",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Resolved 1 packages"));
            assertTrue(result.stderr().contains("Timings for zolt resolve"));
            assertTrue(result.stderr().contains("config read:"));
            assertTrue(result.stderr().contains("resolve graph:"));
            assertTrue(result.stderr().contains("resolvedPackages=1"));
            assertTrue(result.stderr().contains("downloadedArtifacts=2"));
        }
    }

    @Test
    void resolvePrintsJsonTimingsWhenRequested() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"), Map.of());

            CommandResult result = execute(
                    "resolve",
                    "--timings",
                    "--timings-format", "json",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Resolved 1 packages"));
            String[] lines = result.stderr().lines().toArray(String[]::new);
            assertEquals(2, lines.length);
            assertTrue(lines[0].startsWith("{\"command\":\"resolve\""));
            assertTrue(lines[0].contains("\"phase\":\"config read\""));
            assertTrue(lines[0].contains("\"projectRoot\":\"" + jsonPath(projectDir.toAbsolutePath().normalize()) + "\""));
            assertTrue(lines[1].contains("\"phase\":\"resolve graph\""));
            assertTrue(lines[1].contains("\"durationNanos\":"));
            assertTrue(lines[1].contains("\"conflicts\":\"0\""));
            assertTrue(lines[1].contains("\"downloadedArtifacts\":\"2\""));
            assertTrue(lines[1].contains("\"resolvedPackages\":\"1\""));
            assertTrue(lines[1].contains("\"pomCacheMisses\""));
            assertTrue(lines[1].contains("\"rawPomCacheHits\""));
            assertTrue(lines[1].contains("\"pomDownloadMillis\""));
            assertTrue(lines[1].contains("\"jarDownloadMillis\""));
            assertTrue(lines[1].contains("\"pomDownloadNanos\""));
            assertTrue(lines[1].contains("\"jarDownloadNanos\""));
            assertTrue(lines[1].contains("\"rawPomParseNanos\""));
            assertTrue(lines[1].contains("\"effectivePomBuildNanos\""));
            assertTrue(lines[1].contains("\"graphTraversalNanos\""));
            assertTrue(lines[1].contains("\"lockfileAssemblyNanos\""));
            assertTrue(lines[1].contains("\"lockfileWriteNanos\""));
        }
    }

    @Test
    void failedCommandStillPrintsTimingsWhenRequested() {
        CommandResult result = execute("resolve", "--timings", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.toml"));
        assertTrue(result.stderr().contains("Timings for zolt resolve"));
        assertTrue(result.stderr().contains("config read:"));
        assertTrue(result.stderr().contains("status=failed"));
    }

    @Test
    void resolveLockedVerifiesExistingLockfile() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            Files.createDirectories(projectDir);
            Files.writeString(projectDir.resolve("zolt.toml"), """
                    [project]
                    name = "demo"
                    version = "0.1.0"
                    group = "com.example"
                    java = "21"
                    main = "com.example.Main"

                    [repositories]
                    test = "%s"

                    [dependencies]
                    "com.example:app" = "1.0.0"
                    """.formatted(repository.baseUri()));
            CommandResult unlocked = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            CommandResult locked = execute(
                    "resolve",
                    "--locked",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            assertEquals(0, unlocked.exitCode());
            assertEquals(0, locked.exitCode());
            assertTrue(locked.stdout().contains("Verified " + projectDir.resolve("zolt.lock")));
        }
    }

    @Test
    void buildRejectsStaleExistingLockfileBeforeCompiling() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            repository.addArtifact("com.example", "extra", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>extra</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("build-stale-lock");
            Path cacheRoot = tempDir.resolve("build-stale-lock-cache");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"), Map.of());
            CommandResult resolve = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString());
            Path mainSource = projectDir.resolve("src/main/java/com/example/Main.java");
            Files.createDirectories(mainSource.getParent());
            Files.writeString(mainSource, """
                    package com.example;

                    public final class Main {
                    }
                    """);
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of(
                    "com.example:app", "1.0.0",
                    "com.example:extra", "1.0.0"), Map.of());

            CommandResult result = execute(
                    "build",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString());

            assertEquals(0, resolve.exitCode());
            assertEquals(1, result.exitCode());
            assertTrue(result.stderr().contains("zolt.lock is out of date"));
            assertTrue(result.stderr().contains("Run `zolt resolve` to refresh it"));
            assertFalse(Files.exists(projectDir.resolve("target/classes/com/example/Main.class")));
        }
    }

    @Test
    void classpathRejectsStaleExistingLockfileWhenProjectConfigExists() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            repository.addArtifact("com.example", "extra", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>extra</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("classpath-stale-lock");
            Path cacheRoot = tempDir.resolve("classpath-stale-lock-cache");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"), Map.of());
            CommandResult resolve = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString());
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of(
                    "com.example:app", "1.0.0",
                    "com.example:extra", "1.0.0"), Map.of());

            CommandResult result = execute(
                    "classpath",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString(),
                    "compile");

            assertEquals(0, resolve.exitCode());
            assertEquals(1, result.exitCode());
            assertTrue(result.stderr().contains("zolt.lock is out of date"));
            assertTrue(result.stderr().contains("Run `zolt resolve` to refresh it"));
            assertEquals("", result.stdout());
        }
    }

    @Test
    void resolveLockedReportsMissingLockfileClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "resolve",
                "--locked",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Locked resolve requires zolt.lock"));
        assertTrue(result.stderr().contains("Run `zolt resolve` to create it"));
    }

    @Test
    void resolveOfflineReportsMissingCachedArtifactClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(
                projectDir,
                "https://repo.maven.apache.org/maven2",
                Map.of("com.example:missing", "1.0.0"),
                Map.of());

        CommandResult result = execute(
                "resolve",
                "--offline",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Offline mode requires cached POM"));
        assertTrue(result.stderr().contains("Run the command without --offline"));
    }

    @Test
    void addRefreshesLockfileByDefault() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            writeProjectConfig(projectDir, repository.baseUri().toString());

            CommandResult result = execute(
                    "add",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString(),
                    "com.example:app:1.0.0");

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Added dependency com.example:app:1.0.0 to [dependencies]"));
            assertTrue(result.stdout().contains("Resolved 1 packages"));
            assertTrue(result.stdout().contains("Downloaded 2 artifacts"));
            assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
        }
    }

    @Test
    void removeDeletesDependencyAndPrunesUnusedTransitivesFromLockfile() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
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
            repository.addArtifact("com.example", "lib", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>lib</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"), Map.of());

            CommandResult resolve = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());
            String initialLockfile = Files.readString(projectDir.resolve("zolt.lock"));

            CommandResult remove = execute(
                    "remove",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString(),
                    "com.example:app");

            assertEquals(0, resolve.exitCode());
            assertTrue(initialLockfile.contains("com.example:app"));
            assertTrue(initialLockfile.contains("com.example:lib"));
            assertEquals(0, remove.exitCode());
            assertTrue(remove.stdout().contains("Removed dependency com.example:app from [dependencies]"));
            assertTrue(remove.stdout().contains("Resolved 0 packages"));
            String config = Files.readString(projectDir.resolve("zolt.toml"));
            String updatedLockfile = Files.readString(projectDir.resolve("zolt.lock"));
            assertFalse(config.contains("\"com.example:app\" = \"1.0.0\""));
            assertFalse(updatedLockfile.contains("com.example:app"));
            assertFalse(updatedLockfile.contains("com.example:lib"));
        }
    }

    @Test
    void removeDeletesDependencyFromRequestedSectionOnly() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "tool", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>tool</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            writeProjectConfig(
                    projectDir,
                    repository.baseUri().toString(),
                    Map.of("com.example:tool", "1.0.0"),
                    Map.of("com.example:tool", "1.0.0"));

            CommandResult result = execute(
                    "remove",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString(),
                    "test",
                    "com.example:tool");

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Removed dependency com.example:tool from [test.dependencies]"));
            String config = Files.readString(projectDir.resolve("zolt.toml"));
            assertEquals(1, occurrences(config, "\"com.example:tool\" = \"1.0.0\""));
            assertTrue(config.indexOf("[dependencies]") < config.indexOf("\"com.example:tool\" = \"1.0.0\""));
            assertTrue(config.indexOf("\"com.example:tool\" = \"1.0.0\"") < config.indexOf("[test.dependencies]"));
        }
    }

    @Test
    void removeDeletesProcessorDependencyFromRequestedSection() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "processor", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>processor</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            Files.createDirectories(projectDir);
            Files.writeString(projectDir.resolve("zolt.toml"), """
                    [project]
                    name = "demo"
                    version = "0.1.0"
                    group = "com.example"
                    java = "21"

                    [repositories]
                    "local" = "%s"

                    [platforms]

                    [dependencies]
                    "com.example:processor" = "1.0.0"

                    [test.dependencies]

                    [annotationProcessors]
                    "com.example:processor" = "1.0.0"

                    [build]
                    source = "src/main/java"
                    test = "src/test/java"
                    output = "target/classes"
                    testOutput = "target/test-classes"
                    """.formatted(repository.baseUri()));

            CommandResult result = execute(
                    "remove",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString(),
                    "processor",
                    "com.example:processor");

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Removed dependency com.example:processor from [annotationProcessors]"));
            assertTrue(result.stdout().contains("Resolved 1 packages"));
            String config = Files.readString(projectDir.resolve("zolt.toml"));
            assertTrue(config.contains("[dependencies]\n\"com.example:processor\" = \"1.0.0\""));
            assertFalse(config.contains("[annotationProcessors]\n\"com.example:processor\" = \"1.0.0\""));
        }
    }

    @Test
    void platformAddRefreshesLockfileByDefault() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "enterprise-platform", "2026.1.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>enterprise-platform</artifactId>
                      <version>2026.1.0</version>
                      <dependencyManagement>
                        <dependencies>
                          <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>legacy-api</artifactId>
                            <version>1.5.0</version>
                          </dependency>
                        </dependencies>
                      </dependencyManagement>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            writeProjectConfig(projectDir, repository.baseUri().toString());

            CommandResult result = execute(
                    "platform",
                    "add",
                    "--cwd", projectDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString(),
                    "com.example:enterprise-platform:2026.1.0");

            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("Added platform com.example:enterprise-platform:2026.1.0 to [platforms]"));
            assertTrue(result.stdout().contains("Resolved 0 packages"));
            assertTrue(result.stdout().contains("Downloaded 1 artifacts"));
            assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
        }
    }

    @Test
    void ideModelCheckLockReportsFreshLockfileWithoutDiagnostics() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            Path cacheRoot = tempDir.resolve("cache");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"), Map.of());
            CommandResult resolve = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString());
            String lockfile = Files.readString(projectDir.resolve("zolt.lock"));

            CommandResult result = execute(
                    "ide",
                    "model",
                    "--check-lock",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString(),
                    "--format", "json");

            assertEquals(0, resolve.exitCode());
            assertEquals(0, result.exitCode());
            assertEquals("", result.stderr());
            assertTrue(result.stdout().contains("\"diagnostics\": []"));
            assertEquals(lockfile, Files.readString(projectDir.resolve("zolt.lock")));
        }
    }

    @Test
    void ideModelReportsStaleLockfileWithoutRewritingItByDefault() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            repository.addArtifact("com.example", "extra", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>extra</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path projectDir = tempDir.resolve("demo");
            Path cacheRoot = tempDir.resolve("cache");
            writeProjectConfig(projectDir, repository.baseUri().toString(), Map.of("com.example:app", "1.0.0"), Map.of());
            CommandResult resolve = execute(
                    "resolve",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString());
            String lockfile = Files.readString(projectDir.resolve("zolt.lock"));
            writeProjectConfig(
                    projectDir,
                    repository.baseUri().toString(),
                    Map.of(
                            "com.example:app", "1.0.0",
                            "com.example:extra", "1.0.0"),
                    Map.of());

            CommandResult result = execute(
                    "ide",
                    "model",
                    "--cwd", projectDir.toString(),
                    "--cache-root", cacheRoot.toString(),
                    "--format", "json");

            assertEquals(0, resolve.exitCode());
            assertEquals(0, result.exitCode());
            assertEquals("", result.stderr());
            assertTrue(result.stdout().contains("\"code\": \"LOCKFILE_STALE\""));
            assertTrue(result.stdout().contains("zolt.lock is out of date"));
            assertTrue(result.stdout().contains("\"nextStep\": \"Run zolt resolve.\""));
            assertEquals(lockfile, Files.readString(projectDir.resolve("zolt.lock")));
        }
    }

    @Test
    void ideModelWorkspaceReportsStaleRootLockfileByDefault() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path workspaceDir = tempDir.resolve("workspace");
            Path apiDir = workspaceDir.resolve("apps/api");
            Files.createDirectories(apiDir);
            Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                    [workspace]
                    name = "workspace"
                    members = ["apps/api"]

                    [repositories]
                    test = "%s"
                    """.formatted(repository.baseUri()));
            Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """

                    [dependencies]
                    "com.example:app" = "1.0.0"
                    """);
            CommandResult resolve = execute(
                    "resolve",
                    "--workspace",
                    "--cwd", apiDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());
            String existingLockfile = Files.readString(workspaceDir.resolve("zolt.lock"));
            Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                    [workspace]
                    name = "workspace"
                    members = ["apps/api"]

                    [repositories]
                    test = "%s?changed=true"
                    """.formatted(repository.baseUri()));

            CommandResult result = execute(
                    "ide",
                    "model",
                    "--workspace",
                    "--cwd", apiDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString(),
                    "--format", "json");

            assertEquals(0, resolve.exitCode());
            assertEquals(0, result.exitCode());
            assertEquals("", result.stderr());
            assertTrue(result.stdout().contains("\"code\": \"LOCKFILE_STALE\""));
            assertTrue(result.stdout().contains("Workspace zolt.lock is out of date"));
            assertTrue(result.stdout().contains("\"nextStep\": \"Run zolt resolve --workspace.\""));
            assertEquals(existingLockfile, Files.readString(workspaceDir.resolve("zolt.lock")));
        }
    }

    @Test
    void buildWorkspaceRejectsStaleGeneratedRootLockfileBeforeCompiling() throws IOException {
        try (TestRepository repository = TestRepository.start()) {
            repository.addArtifact("com.example", "app", "1.0.0", """
                    <project>
                      <groupId>com.example</groupId>
                      <artifactId>app</artifactId>
                      <version>1.0.0</version>
                    </project>
                    """);
            Path workspaceDir = tempDir.resolve("workspace");
            Path apiDir = workspaceDir.resolve("apps/api");
            Files.createDirectories(apiDir);
            Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                    [workspace]
                    name = "workspace"
                    members = ["apps/api"]

                    [repositories]
                    test = "%s"
                    """.formatted(repository.baseUri()));
            Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """

                    [dependencies]
                    "com.example:app" = "1.0.0"
                    """);
            CommandResult resolve = execute(
                    "resolve",
                    "--workspace",
                    "--cwd", apiDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());
            String existingLockfile = Files.readString(workspaceDir.resolve("zolt.lock"));
            Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                    [workspace]
                    name = "workspace"
                    members = ["apps/api"]

                    [repositories]
                    test = "%s?changed=true"
                    """.formatted(repository.baseUri()));
            Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
            Files.createDirectories(apiSource.getParent());
            Files.writeString(apiSource, """
                    package com.example.api;

                    public final class Api {
                    }
                    """);

            CommandResult result = execute(
                    "build",
                    "--workspace",
                    "--cwd", apiDir.toString(),
                    "--cache-root", tempDir.resolve("cache").toString());

            assertEquals(0, resolve.exitCode());
            assertEquals(1, result.exitCode());
            assertTrue(existingLockfile.contains("projectResolutionFingerprint = \"sha256:"));
            assertTrue(result.stderr().contains("Workspace zolt.lock is out of date"));
            assertEquals(existingLockfile, Files.readString(workspaceDir.resolve("zolt.lock")));
            assertFalse(Files.exists(apiDir.resolve("target/classes/com/example/api/Api.class")));
        }
    }

    private static CommandResult execute(String... args) {
        CommandLine commandLine = ZoltCli.newCommandLine();
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        commandLine.setOut(new PrintWriter(stdout));
        commandLine.setErr(new PrintWriter(stderr));

        int exitCode = commandLine.execute(args);

        return new CommandResult(exitCode, stdout.toString(), stderr.toString());
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }

    private static void writeProjectConfig(Path projectDir, String repositoryUrl) throws IOException {
        writeProjectConfig(projectDir, repositoryUrl, currentJavaMajorVersion(), Map.of(), Map.of());
    }

    private static void writeProjectConfig(
            Path projectDir,
            String repositoryUrl,
            Map<String, String> dependencies,
            Map<String, String> testDependencies) throws IOException {
        writeProjectConfig(projectDir, repositoryUrl, currentJavaMajorVersion(), dependencies, testDependencies);
    }

    private static void writeProjectConfig(
            Path projectDir,
            String repositoryUrl,
            String javaVersion) throws IOException {
        writeProjectConfig(projectDir, repositoryUrl, javaVersion, Map.of(), Map.of());
    }

    private static void writeProjectConfig(
            Path projectDir,
            String repositoryUrl,
            String javaVersion,
            Map<String, String> dependencies,
            Map<String, String> testDependencies) throws IOException {
        Files.createDirectories(projectDir);
        StringBuilder config = new StringBuilder("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                main = "com.example.Main"

                [repositories]
                test = "%s"

                [dependencies]
                """.formatted(javaVersion, repositoryUrl));
        appendDependencies(config, dependencies);
        config.append("\n[test.dependencies]\n");
        appendDependencies(config, testDependencies);
        config.append("""

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);
        Files.writeString(projectDir.resolve("zolt.toml"), config.toString());
    }

    private static void writeProjectConfigWithoutMain(Path projectDir, String repositoryUrl) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"

                [repositories]
                test = "%s"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """.formatted(currentJavaMajorVersion(), repositoryUrl));
    }

    private static void appendDependencies(StringBuilder config, Map<String, String> dependencies) {
        dependencies.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> config.append('"')
                        .append(entry.getKey())
                        .append("\" = \"")
                        .append(entry.getValue())
                        .append("\"\n"));
    }

    private static String memberConfig(String name) {
        return """
                [project]
                name = "%s"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                """.formatted(name, currentJavaMajorVersion());
    }

    private static String pom(String groupId, String artifactId, String version) {
        return """
                <project>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(groupId, artifactId, version);
    }

    private static void writeMainSource(Path projectDir, String content) throws IOException {
        Path source = projectDir.resolve("src/main/java/com/example/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }

    private static void createJarWithTextEntry(Path jar, String entryName, String text) throws IOException {
        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(entryName));
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int index = value.indexOf(needle);
        while (index >= 0) {
            count++;
            index = value.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static String jsonPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static final class TestRepository implements AutoCloseable {
        private final HttpServer server;
        private final Map<String, byte[]> responses = new HashMap<>();
        private final Map<String, byte[]> uploads = new HashMap<>();
        private final URI baseUri;

        private TestRepository(HttpServer server) {
            this.server = server;
            this.baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/maven2/");
        }

        static TestRepository start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            TestRepository repository = new TestRepository(server);
            server.createContext("/", repository::handle);
            server.start();
            return repository;
        }

        URI baseUri() {
            return baseUri;
        }

        void addArtifact(String groupId, String artifactId, String version, String pom) {
            String base = "/maven2/"
                    + groupId.replace('.', '/')
                    + "/"
                    + artifactId
                    + "/"
                    + version
                    + "/"
                    + artifactId
                    + "-"
                    + version;
            responses.put(base + ".pom", pom.getBytes(StandardCharsets.UTF_8));
            responses.put(base + ".jar", new byte[] {0x50, 0x4b, 0x03, 0x04});
        }

        byte[] uploaded(String path) {
            byte[] bytes = uploads.get(path);
            if (bytes == null) {
                throw new AssertionError("No upload recorded for " + path);
            }
            return bytes.clone();
        }

        private void handle(HttpExchange exchange) throws IOException {
            if ("PUT".equals(exchange.getRequestMethod())) {
                uploads.put(exchange.getRequestURI().getPath(), exchange.getRequestBody().readAllBytes());
                respond(exchange, 201, "created".getBytes(StandardCharsets.UTF_8));
                return;
            }
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

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
