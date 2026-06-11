package com.zolt.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class ZoltCliTest {
    @TempDir
    private Path tempDir;

    @Test
    void versionPrintsZoltVersion() {
        CommandResult result = execute("--version");

        assertEquals(0, result.exitCode());
        assertEquals("0.1.0-SNAPSHOT\n", result.stdout());
    }

    @Test
    void versionCommandPrintsZoltVersion() {
        CommandResult result = execute("version");

        assertEquals(0, result.exitCode());
        assertEquals("0.1.0-SNAPSHOT\n", result.stdout());
    }

    @Test
    void updateExplainsFutureSelfUpdatePath() {
        CommandResult result = execute("update");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("zolt update is not available yet."));
        assertTrue(result.stdout().contains("verified native archive"));
        assertTrue(result.stdout().contains("followUps/-design-zolt-update-command.md"));
        assertEquals("", result.stderr());
    }

    @Test
    void helpListsMvpCommands() {
        CommandResult result = execute("help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("The modern Java build toolkit."));
        assertTrue(result.stdout().contains("init"));
        assertTrue(result.stdout().contains("resolve"));
        assertTrue(result.stdout().contains("check"));
        assertTrue(result.stdout().contains("build"));
        assertTrue(result.stdout().contains("doctor"));
    }

    @Test
    void registersMvpCommandSurface() {
        Set<String> subcommands = ZoltCli.newCommandLine().getSubcommands().keySet();

        assertTrue(subcommands.containsAll(Set.of(
                "help",
                "init",
                "version",
                "update",
                "check",
                "add",
                "remove",
                "platform",
                "resolve",
                "tree",
                "why",
                "policy",
                "conflicts",
                "explain",
                "plan",
                "classpath",
                "ide",
                "quarkus",
                "build",
                "run",
                "test",
                "package",
                "run-package",
                "native",
                "native-smoke",
                "release-archive",
                "release-verify",
                "self-check",
                "self-parity",
                "clean",
                "doctor")));
    }

    @Test
    void planReportsTypedPipelineAndBlockersWithoutExecutingWork() throws IOException {
        Path projectDir = tempDir.resolve("plan-blocked");
        Files.createDirectories(projectDir.resolve("src/main/openapi"));
        Files.writeString(projectDir.resolve("src/main/openapi/api.yaml"), "openapi: 3.1.0\n");
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("plan-blocked")
                + generatedSourceConfig("main", "openapi", "target/generated/sources/openapi", "src/main/openapi/api.yaml", true)
                + """

                [resources.filtering]
                enabled = true
                includes = ["**/*.properties"]
                missing = "fail"

                [resources.tokens]
                projectVersion = { project = "version" }

                [package]
                mode = "spring-boot-war"
                """);

        CommandResult result = execute("plan", "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("Zolt plan"));
        assertTrue(result.stdout().contains("Target: package"));
        assertTrue(result.stdout().contains("Status: blocked"));
        assertTrue(result.stdout().contains("- lockfile [resolve] blocked"));
        assertTrue(result.stdout().contains("blocker missing-lockfile: zolt.lock is missing"));
        assertTrue(result.stdout().contains("- generate-main-openapi [generated-source] blocked"));
        assertTrue(result.stdout().contains("blocker missing-generated-source-output"));
        assertTrue(result.stdout().contains("- process-main-resources [resources] ready"));
        assertTrue(result.stdout().contains("tokens: [projectVersion]"));
        assertTrue(result.stdout().contains("- assemble-package [package] blocked"));
        assertTrue(result.stdout().contains("blocker missing-main-class"));
        assertEquals("", result.stderr());
    }

    @Test
    void planJsonRedactsTestEnvironmentValues() throws IOException {
        Path projectDir = tempDir.resolve("plan-json");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("plan-json") + """

                [test.runtime]
                jvmArgs = ["--add-opens=java.base/java.lang=ALL-UNNAMED"]
                systemProperties = { "logs.dir" = "${project.root}/test-logs" }
                environment = { TZ = "America/Chicago" }
                events = ["failed", "skipped"]
                """);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "plan",
                "--target", "test",
                "--reports-dir", "target/test-reports",
                "--format", "json",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"target\": \"test\""));
        assertTrue(result.stdout().contains("\"id\": \"run-tests\""));
        assertTrue(result.stdout().contains("\"target/test-reports\""));
        assertTrue(result.stdout().contains("\"environment: [TZ] (values redacted)\""));
        assertFalse(result.stdout().contains("America/Chicago"));
        assertEquals("", result.stderr());
    }

    @Test
    void planCiIncludesExplicitCoverageAndPublishNodes() throws IOException {
        Path projectDir = tempDir.resolve("plan-ci");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("plan-ci"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute("plan", "--target", "ci", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("- coverage [coverage] planned"));
        assertTrue(result.stdout().contains("mode: dry-run"));
        assertTrue(result.stdout().contains("- publish-dry-run [publish] planned"));
        assertTrue(result.stdout().contains("mode: dry-run"));
    }

    @Test
    void planReportsStaleGeneratedSourceOutputs() throws IOException {
        Path projectDir = tempDir.resolve("plan-stale-generated-source");
        Path input = projectDir.resolve("src/main/openapi/api.yaml");
        Path output = projectDir.resolve("target/generated/sources/openapi/com/example/GeneratedApi.java");
        Files.createDirectories(input.getParent());
        Files.createDirectories(output.getParent());
        Files.writeString(input, "openapi: 3.1.0\n");
        Files.writeString(output, "package com.example; public final class GeneratedApi {}\n");
        Files.setLastModifiedTime(output, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(input, FileTime.fromMillis(2_000));
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("plan-stale-generated-source")
                + generatedSourceConfig("main", "openapi", "target/generated/sources/openapi", "src/main/openapi/api.yaml", true));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute("plan", "--target", "build", "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("freshness: stale"));
        assertTrue(result.stdout().contains("blocker stale-generated-source-output"));
        assertTrue(result.stdout().contains("Required generated source output `target/generated/sources/openapi` is older"));
    }

    @Test
    void resolveRejectsUnknownRepositoryOverlay() throws IOException {
        Path projectDir = tempDir.resolve("resolve-unknown-overlay");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("resolve-unknown-overlay"));

        CommandResult result = execute(
                "resolve",
                "--cwd", projectDir.toString(),
                "--repository-overlay", "gradle-cache");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Unsupported repository overlay `gradle-cache`"));
        assertTrue(result.stderr().contains("Supported overlays: maven-local"));
    }

    @Test
    void resolveRejectsConflictingLocalOverlayOptions() throws IOException {
        Path projectDir = tempDir.resolve("resolve-conflicting-overlays");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("resolve-conflicting-overlays"));

        CommandResult result = execute(
                "resolve",
                "--cwd", projectDir.toString(),
                "--repository-overlay", "local-maven",
                "--no-local-overlays");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Cannot combine local repository overlays with local-overlay rejection"));
        assertTrue(result.stderr().contains("Remove --repository-overlay or remove --no-local-overlays"));
    }

    @Test
    void checkSucceedsForTypedProjectModel() throws IOException {
        Path projectDir = tempDir.resolve("check-demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-demo"));

        CommandResult result = execute("check", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals(
                "ok command-surface check-demo zolt check uses typed Zolt project data; no Maven, Gradle, or shell hooks are run.\n",
                result.stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void checkJsonOutputUsesStableResultShape() throws IOException {
        Path projectDir = tempDir.resolve("check-json");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-json"));

        CommandResult result = execute("check", "--format", "json", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().startsWith("{\"status\":\"ok\",\"projectRoot\":\""));
        assertTrue(result.stdout().contains("\"workspace\":false"));
        assertTrue(result.stdout().contains("\"id\":\"command-surface\""));
        assertTrue(result.stdout().contains("\"severity\":\"info\""));
        assertTrue(result.stdout().contains("\"status\":\"passed\""));
        assertTrue(result.stdout().contains("\"subject\":\"check-json\""));
        assertTrue(result.stdout().endsWith("]}\n"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkPackageContentsReportsSuspiciousWarContainerDependency() throws IOException {
        Path projectDir = tempDir.resolve("check-package-contents-war");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-package-contents-war") + """

                [package]
                mode = "war"
                """);
        writePackagePlanLockfile(projectDir, false, true);

        CommandResult result = execute(
                "check",
                "--cwd", projectDir.toString(),
                "--check", "package-contents");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error package-contents org.apache.tomcat.embed:tomcat-embed-core:10.1.40"));
        assertTrue(result.stdout().contains("Container-style dependency `org.apache.tomcat.embed:tomcat-embed-core:10.1.40` is packaged in WEB-INF/lib/tomcat-embed-core-10.1.40.jar by package rule `war-runtime-lib`."));
        assertTrue(result.stdout().contains("next: Move it to [provided.dependencies]"));
    }

    @Test
    void checkPackageContentsReportsPolicyEffects() throws IOException {
        Path projectDir = tempDir.resolve("check-package-contents-policy");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-package-contents-policy") + """

                [package]
                mode = "spring-boot"
                """);
        writePackagePlanLockfile(projectDir, true, false);

        CommandResult result = execute(
                "check",
                "--cwd", projectDir.toString(),
                "--check", "package-contents");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok package-contents check-package-contents-policy Package mode `spring-boot` has"));
        assertTrue(result.stdout().contains("1 dependencies include dependency policy effects."));
    }

    @Test
    void checkGeneratedSourcesReportsNoDeclaredSteps() throws IOException {
        Path projectDir = tempDir.resolve("check-no-generated-sources");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-no-generated-sources"));

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "generated-sources");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok generated-sources check-no-generated-sources No declared generated-source steps require validation."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkGeneratedSourcesPassesForExistingDeclaredRoots() throws IOException {
        Path projectDir = tempDir.resolve("check-generated-sources-ok");
        Files.createDirectories(projectDir.resolve("target/generated/sources/openapi/com/example"));
        Files.createDirectories(projectDir.resolve("src/main/openapi"));
        Files.writeString(projectDir.resolve("target/generated/sources/openapi/com/example/GeneratedApi.java"), """
                package com.example;
                public final class GeneratedApi {}
                """);
        Files.writeString(projectDir.resolve("src/main/openapi/api.yaml"), "openapi: 3.1.0\n");
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-generated-sources-ok")
                + generatedSourceConfig("main", "openapi", "target/generated/sources/openapi", "src/main/openapi/api.yaml", true));

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "generated-sources");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok generated-sources [generated.main.openapi] Generated source root `target/generated/sources/openapi` is declared"));
        assertTrue(result.stdout().contains("exported as IDE source root `generated-main-openapi`"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkGeneratedSourcesReportsMalformedPaths() throws IOException {
        Path projectDir = tempDir.resolve("check-generated-sources-invalid-path");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-generated-sources-invalid-path")
                + generatedSourceConfig("main", "openapi", "../generated/openapi", "src/main/openapi/api.yaml", true));

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "generated-sources");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error generated-sources [generated.main.openapi].output Invalid generated source output path `../generated/openapi`."));
        assertTrue(result.stdout().contains("next: Use a project-relative path under the project directory."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkGeneratedSourcesReportsMissingRequiredOutputs() throws IOException {
        Path projectDir = tempDir.resolve("check-generated-sources-missing-required");
        Files.createDirectories(projectDir.resolve("src/main/openapi"));
        Files.writeString(projectDir.resolve("src/main/openapi/api.yaml"), "openapi: 3.1.0\n");
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-generated-sources-missing-required")
                + generatedSourceConfig("main", "openapi", "target/generated/sources/openapi", "src/main/openapi/api.yaml", true));

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "generated-sources");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error generated-sources [generated.main.openapi] Generated source root `target/generated/sources/openapi` is missing."));
        assertTrue(result.stdout().contains("next: Run the generator that produces it, commit the generated sources"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkGeneratedSourcesSkipsOptionalMissingOutputs() throws IOException {
        Path projectDir = tempDir.resolve("check-generated-sources-optional");
        Files.createDirectories(projectDir.resolve("src/test/fixtures"));
        Files.writeString(projectDir.resolve("src/test/fixtures/schema.json"), "{}\n");
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-generated-sources-optional")
                + generatedSourceConfig("test", "fixtures", "target/generated/test-sources/fixtures", "src/test/fixtures/schema.json", false));

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "generated-sources");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("skip generated-sources [generated.test.fixtures] Optional generated source root `target/generated/test-sources/fixtures` is missing."));
        assertTrue(result.stdout().contains("next: Generate it when needed, or set required = true"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkWorkspaceGeneratedSourcesIdentifyMemberPaths() throws IOException {
        Path workspaceDir = tempDir.resolve("check-workspace-generated-sources");
        Path apiDir = workspaceDir.resolve("modules/api");
        Path implDir = workspaceDir.resolve("modules/impl");
        Files.createDirectories(apiDir.resolve("target/generated/sources/openapi"));
        Files.createDirectories(apiDir.resolve("src/main/openapi"));
        Files.createDirectories(implDir);
        Files.writeString(apiDir.resolve("src/main/openapi/api.yaml"), "openapi: 3.1.0\n");
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "check-workspace-generated-sources"
                members = ["modules/api", "modules/impl"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api")
                + generatedSourceConfig("main", "openapi", "target/generated/sources/openapi", "src/main/openapi/api.yaml", true));
        Files.writeString(implDir.resolve("zolt.toml"), memberConfig("impl"));

        CommandResult result = execute(
                "check",
                "--workspace",
                "--member", "modules/api",
                "--cwd", workspaceDir.toString(),
                "--check", "generated-sources");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok generated-sources modules/api [generated.main.openapi] Generated source root `target/generated/sources/openapi` is declared"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkPackageAndManifestMetadataPassForLibraryProject() throws IOException {
        Path projectDir = tempDir.resolve("check-library-metadata");
        Files.createDirectories(projectDir.resolve("src/main/java/com/example"));
        Files.createDirectories(projectDir.resolve("src/test/java/com/example"));
        Files.writeString(projectDir.resolve("src/main/java/com/example/Library.java"), """
                package com.example;
                public final class Library {}
                """);
        Files.writeString(projectDir.resolve("src/test/java/com/example/LibraryTest.java"), """
                package com.example;
                public final class LibraryTest {}
                """);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-library-metadata")
                + libraryPackageConfig("Check Library Metadata", "com.example.check.library", true));

        CommandResult result = execute(
                "check",
                "--cwd", projectDir.toString(),
                "--check", "package-metadata",
                "--check", "manifest-metadata");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok package-metadata check-library-metadata Library package metadata is complete."));
        assertTrue(result.stdout().contains("ok manifest-metadata check-library-metadata Library manifest metadata is deterministic."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkPackageMetadataReportsMissingLibraryArtifactSetting() throws IOException {
        Path projectDir = tempDir.resolve("check-library-missing-tests");
        Files.createDirectories(projectDir.resolve("src/main/java/com/example"));
        Files.createDirectories(projectDir.resolve("src/test/java/com/example"));
        Files.writeString(projectDir.resolve("src/main/java/com/example/Library.java"), """
                package com.example;
                public final class Library {}
                """);
        Files.writeString(projectDir.resolve("src/test/java/com/example/LibraryTest.java"), """
                package com.example;
                public final class LibraryTest {}
                """);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-library-missing-tests")
                + libraryPackageConfig("Check Library Missing Tests", "com.example.check.missing.tests", false));

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "package-metadata");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error package-metadata [package].tests Test sources are present"));
        assertTrue(result.stdout().contains("next: Set [package].tests = true"));
        assertEquals("", result.stderr());
    }

    @Test
    void checkManifestMetadataReportsMissingAutomaticModuleName() throws IOException {
        Path projectDir = tempDir.resolve("check-library-missing-module");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-library-missing-module") + """

                [package]
                mode = "thin"
                sources = true
                javadoc = true

                [package.metadata]
                name = "Check Library Missing Module"
                description = "Fixture"
                url = "https://example.com/check-library-missing-module"
                license = "Apache-2.0"
                developers = ["Zolt Team"]
                scm = "https://example.com/check-library-missing-module.git"
                issues = "https://example.com/check-library-missing-module/issues"
                """);

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "manifest-metadata");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error manifest-metadata [package.manifest].Automatic-Module-Name"));
        assertTrue(result.stdout().contains("next: Add [package.manifest].\"Automatic-Module-Name\""));
        assertEquals("", result.stderr());
    }

    @Test
    void checkManifestMetadataReportsZoltOwnedAttributes() throws IOException {
        Path projectDir = tempDir.resolve("check-library-owned-manifest");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-library-owned-manifest")
                + libraryPackageConfig("Check Library Owned Manifest", "com.example.check.owned", false)
                + """
                "Main-Class" = "com.example.Main"
                """);

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "manifest-metadata");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error manifest-metadata [package.manifest].Main-Class Manifest attribute `Main-Class` is owned by Zolt."));
        assertTrue(result.stdout().contains("next: Remove it from [package.manifest]; use [project].main for Main-Class."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkWorkspacePackageAndManifestMetadataIdentifyMemberPaths() throws IOException {
        Path workspaceDir = tempDir.resolve("check-workspace-library-metadata");
        Path apiDir = workspaceDir.resolve("modules/api");
        Path implDir = workspaceDir.resolve("modules/impl");
        Files.createDirectories(apiDir);
        Files.createDirectories(implDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "check-workspace-library-metadata"
                members = ["modules/api", "modules/impl"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api")
                + libraryPackageConfig("API Library", "com.example.api", false));
        Files.writeString(implDir.resolve("zolt.toml"), memberConfig("impl")
                + libraryPackageConfig("Implementation Library", "com.example.impl", false));

        CommandResult result = execute(
                "check",
                "--workspace",
                "--member", "modules/impl",
                "--cwd", workspaceDir.toString(),
                "--check", "package-metadata",
                "--check", "manifest-metadata");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok package-metadata modules/impl impl Library package metadata is complete."));
        assertTrue(result.stdout().contains("ok manifest-metadata modules/impl impl Library manifest metadata is deterministic."));
        assertEquals("", result.stderr());
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
        WorkspaceApplicationFixture fixture = workspaceApplicationFixture("check-workspace");

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
    void checkLockfileReportsMissingLockfile() throws IOException {
        Path projectDir = tempDir.resolve("check-missing-lock");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-missing-lock"));

        CommandResult result = execute("check", "--cwd", projectDir.toString(), "--check", "lockfile");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error lockfile zolt.lock zolt.lock is missing."));
        assertTrue(result.stdout().contains("next: Run `zolt resolve`."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkLockfilePassesWhenLockedResolveMatches() throws IOException {
        Path projectDir = tempDir.resolve("check-lock-ok");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-lock-ok"));
        CommandResult resolve = execute("resolve", "--cwd", projectDir.toString(), "--cache-root", tempDir.resolve("cache").toString());
        assertEquals(0, resolve.exitCode());

        CommandResult result = execute(
                "check",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--check", "lockfile");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok lockfile zolt.lock zolt.lock matches zolt.toml."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkLockfileReportsStaleLockfile() throws IOException {
        Path projectDir = tempDir.resolve("check-lock-stale");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-lock-stale"));
        CommandResult resolve = execute("resolve", "--cwd", projectDir.toString(), "--cache-root", tempDir.resolve("cache").toString());
        assertEquals(0, resolve.exitCode());
        Files.writeString(projectDir.resolve("zolt.lock"), Files.readString(projectDir.resolve("zolt.lock")) + "# stale\n");

        CommandResult result = execute(
                "check",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--check", "lockfile");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error lockfile zolt.lock zolt.lock is out of date."));
        assertTrue(result.stdout().contains("next: Run `zolt resolve`."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkWorkspaceLockfileVerifiesRootLockfile() throws IOException {
        Path workspaceDir = tempDir.resolve("check-workspace-lock");
        Path memberDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(memberDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "check-workspace-lock"
                members = ["modules/core"]
                """);
        Files.writeString(memberDir.resolve("zolt.toml"), memberConfig("core"));
        CommandResult resolve = execute("resolve", "--workspace", "--cwd", workspaceDir.toString(), "--cache-root", tempDir.resolve("cache").toString());
        assertEquals(0, resolve.exitCode());

        CommandResult result = execute(
                "check",
                "--workspace",
                "--cwd", workspaceDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--check", "lockfile");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok lockfile zolt.lock Workspace zolt.lock matches zolt-workspace.toml and member zolt.toml files."));
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
    void testHelpShowsSelectionOptions() {
        CommandResult result = execute("test", "--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("--test"));
        assertTrue(result.stdout().contains("--tests"));
        assertTrue(result.stdout().contains("--include-tag"));
        assertTrue(result.stdout().contains("--exclude-tag"));
        assertTrue(result.stdout().contains("--jvm-arg"));
        assertTrue(result.stdout().contains("--test-event"));
        assertTrue(result.stdout().contains("--reports-dir"));
    }

    @Test
    void testRejectsInvalidSelectionBeforeReadingProjectConfig() {
        CommandResult result = execute("test", "--cwd", tempDir.toString(), "--test", "*ServiceTest");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Invalid --test selector `*ServiceTest`"));
        assertTrue(result.stderr().contains("Use --tests for class-name patterns"));
        assertFalse(result.stderr().contains("Could not read zolt.toml"));
    }

    @Test
    void testRejectsInvalidJvmArgBeforeReadingProjectConfig() {
        CommandResult result = execute("test", "--cwd", tempDir.toString(), "--jvm-arg", "-classpath");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Zolt owns the test classpath"));
        assertFalse(result.stderr().contains("Could not read zolt.toml"));
    }

    @Test
    void testRejectsInvalidEventBeforeReadingProjectConfig() {
        CommandResult result = execute("test", "--cwd", tempDir.toString(), "--test-event", "verbose");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Unsupported test runtime event `verbose`"));
        assertTrue(result.stderr().contains("passed, skipped, failed"));
        assertFalse(result.stderr().contains("Could not read zolt.toml"));
    }

    @Test
    void explainHelpShowsMigrationAuditCommand() {
        CommandResult result = execute("explain", "--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Audit a Maven or Gradle project for future Zolt migration."));
        assertTrue(result.stdout().contains("--format"));
        assertTrue(result.stdout().contains("--source"));
    }

    @Test
    void explainTextPlaceholderIsActionableWhenSourceIsUnknown() {
        CommandResult result = execute("explain", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("zolt explain is not implemented yet."));
        assertTrue(result.stdout().contains("audit Maven and Gradle project metadata statically"));
        assertTrue(result.stdout().contains("This command will not execute Maven or Gradle"));
        assertTrue(result.stdout().contains("Requested source: auto"));
        assertTrue(result.stdout().contains("Project root: " + tempDir.toAbsolutePath().normalize()));
        assertTrue(result.stdout().contains("followUps/-add-zolt-explain-command-scaffold.md"));
        assertEquals("", result.stderr());
    }

    @Test
    void explainGradleJsonInspectsBuildStatically() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'demo'\n");
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'com.google.guava:guava:33.4.8-jre' }
                """);

        CommandResult result = execute(
                "explain",
                "--cwd", tempDir.toString(),
                "--source", "gradle",
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().startsWith("{\n  \"schemaVersion\": 1,"));
        assertTrue(result.stdout().contains("\"source\": \"gradle\""));
        assertTrue(result.stdout().contains("\"root\": \"" + jsonPath(tempDir.toAbsolutePath().normalize()) + "\""));
        assertTrue(result.stdout().contains("\"resolvedCoordinate\": \"com.google.guava:guava:33.4.8-jre\""));
        assertEquals("", result.stderr());
    }

    @Test
    void explainMavenTextInspectsPomStatically() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                      <version>33.4.8-jre</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        CommandResult result = execute("explain", "--cwd", tempDir.toString(), "--source", "maven");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Zolt explain: Maven project"));
        assertTrue(result.stdout().contains("Projects: 1"));
        assertTrue(result.stdout().contains("demo, packaging=jar, java=21"));
        assertTrue(result.stdout().contains("dependencies: 1"));
        assertTrue(result.stdout().contains("did not execute Maven"));
        assertEquals("", result.stderr());
    }

    @Test
    void explainMavenJsonInspectsPomDeterministically() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <artifactId>demo</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                      <version>5.11.4</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);

        CommandResult result = execute(
                "explain",
                "--cwd", tempDir.toString(),
                "--source", "maven",
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().startsWith("{\n  \"schemaVersion\": 1,"));
        assertTrue(result.stdout().contains("\"source\": \"maven\""));
        assertTrue(result.stdout().contains("\"root\": \"" + jsonPath(tempDir.toAbsolutePath().normalize()) + "\""));
        assertTrue(result.stdout().contains("\"coordinate\": \"org.junit.jupiter:junit-jupiter:5.11.4\""));
        assertEquals("", result.stderr());
    }

    @Test
    void explainMavenReportsMalformedPomCleanly() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project><artifactId>broken</project>");

        CommandResult result = execute("explain", "--cwd", tempDir.toString(), "--source", "maven");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not inspect Maven project."));
        assertTrue(result.stderr().contains("Fix malformed POM XML"));
    }

    @Test
    void explainRejectsInvalidFormatClearly() {
        CommandResult result = execute("explain", "--format", "xml");

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("Invalid value for option '--format'"));
        assertTrue(result.stderr().contains("TEXT"));
        assertTrue(result.stderr().contains("JSON"));
    }

    @Test
    void explainRejectsInvalidSourceClearly() {
        CommandResult result = execute("explain", "--source", "ant");

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("Invalid value for option '--source'"));
        assertTrue(result.stderr().contains("AUTO"));
        assertTrue(result.stderr().contains("MAVEN"));
        assertTrue(result.stderr().contains("GRADLE"));
    }

    @Test
    void explainScaffoldDoesNotExecuteMavenOrGradleWrappers() throws IOException {
        Path projectDir = tempDir.resolve("legacy");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("pom.xml"), """
                <project>
                  <artifactId>legacy</artifactId>
                </project>
                """);
        Path marker = projectDir.resolve("executed.txt");
        Path mvnw = projectDir.resolve("mvnw");
        Path gradlew = projectDir.resolve("gradlew");
        Files.writeString(mvnw, "#!/usr/bin/env sh\nprintf mvn > '" + marker + "'\n");
        Files.writeString(gradlew, "#!/usr/bin/env sh\nprintf gradle > '" + marker + "'\n");
        assertTrue(mvnw.toFile().setExecutable(true));
        assertTrue(gradlew.toFile().setExecutable(true));

        CommandResult maven = execute("explain", "--cwd", projectDir.toString(), "--source", "maven");
        CommandResult gradle = execute("explain", "--cwd", projectDir.toString(), "--source", "gradle");

        assertEquals(0, maven.exitCode());
        assertEquals(1, gradle.exitCode());
        assertFalse(Files.exists(marker));
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
    void ideModelPrintsNestedJsonTimingsWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("ide-timings");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "ide", "model",
                "--format", "json",
                "--timings",
                "--timings-format", "json",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"schemaVersion\": 1"));
        assertTrue(result.stderr().contains("\"phase\":\"read ide project config\""));
        assertTrue(result.stderr().contains("\"phase\":\"build ide classpaths\""));
        assertTrue(result.stderr().contains("\"phase\":\"build ide framework model\""));
        assertTrue(result.stderr().contains("\"phase\":\"assemble ide model\""));
        assertTrue(result.stderr().contains("\"phase\":\"ide model export\""));
        assertTrue(result.stderr().contains("\"phase\":\"ide model json\""));
        assertTrue(result.stderr().contains("\"depth\":1"));
        assertTrue(result.stderr().contains("\"testClasspathEntries\""));
    }

    @Test
    void workspaceIdeModelPrintsNestedJsonTimingsWhenRequested() throws IOException {
        WorkspaceApplicationFixture fixture = workspaceApplicationFixture("workspace-ide-timings");

        CommandResult result = execute(
                "ide", "model",
                "--workspace",
                "--format", "json",
                "--timings",
                "--timings-format", "json",
                "--cwd", fixture.workspaceDir().toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"workspace\": {"));
        assertTrue(result.stderr().contains("\"phase\":\"discover ide workspace\""));
        assertTrue(result.stderr().contains("\"phase\":\"read workspace ide lock\""));
        assertTrue(result.stderr().contains("\"phase\":\"plan workspace ide classpaths\""));
        assertTrue(result.stderr().contains("\"phase\":\"export workspace ide projects\""));
        assertTrue(result.stderr().contains("\"phase\":\"export workspace ide edges\""));
        assertTrue(result.stderr().contains("\"phase\":\"assemble workspace ide model\""));
        assertTrue(result.stderr().contains("\"phase\":\"ide model export\""));
        assertTrue(result.stderr().contains("\"phase\":\"ide model json\""));
        assertTrue(result.stderr().contains("\"depth\":1"));
        assertTrue(result.stderr().contains("\"projects\":\"2\""));
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
    void addAddsCompileDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "com.google.guava:guava:33.4.0-jre");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Added dependency com.google.guava:guava:33.4.0-jre to [dependencies]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("\"com.google.guava:guava\" = \"33.4.0-jre\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsTestDependencyWithoutDuplicatingExistingEntry() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult first = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "test",
                "org.junit.jupiter:junit-jupiter:5.11.4");
        CommandResult second = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "test",
                "org.junit.jupiter:junit-jupiter:5.11.4");

        assertEquals(0, first.exitCode());
        assertEquals(0, second.exitCode());
        assertTrue(first.stdout().contains("Added dependency org.junit.jupiter:junit-jupiter:5.11.4 to [test.dependencies]"));
        assertTrue(second.stdout().contains("already exists in [test.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertEquals(1, occurrences(config, "\"org.junit.jupiter:junit-jupiter\" = \"5.11.4\""));
    }

    @Test
    void addAddsManagedDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "com.example:legacy-api");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency com.example:legacy-api with a platform-managed version to [dependencies]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("\"com.example:legacy-api\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsApiDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                "central" = "https://repo.maven.apache.org/maven2"

                [platforms]

                [dependencies]
                "com.example:contract" = "1.0.0"

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "api",
                "com.example:contract:2.0.0");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Updated dependency com.example:contract from 1.0.0 to 2.0.0 in [api.dependencies]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[api.dependencies]\n\"com.example:contract\" = \"2.0.0\""));
        assertFalse(config.contains("[dependencies]\n\"com.example:contract\" = \"1.0.0\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsManagedApiDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "api",
                "com.example:contract");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency com.example:contract with a platform-managed version to [api.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[api.dependencies]\n\"com.example:contract\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsRuntimeDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "runtime",
                "com.h2database:h2");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency com.h2database:h2 with a platform-managed version to [runtime.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[runtime.dependencies]\n\"com.h2database:h2\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsProvidedDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "provided",
                "jakarta.servlet:jakarta.servlet-api:6.1.0");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency jakarta.servlet:jakarta.servlet-api:6.1.0 to [provided.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[provided.dependencies]\n\"jakarta.servlet:jakarta.servlet-api\" = \"6.1.0\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsDevDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "dev",
                "org.springframework.boot:spring-boot-devtools");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency org.springframework.boot:spring-boot-devtools with a platform-managed version to [dev.dependencies]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[dev.dependencies]\n\"org.springframework.boot:spring-boot-devtools\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsProcessorDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "processor",
                "org.mapstruct:mapstruct-processor:1.6.3");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency org.mapstruct:mapstruct-processor:1.6.3 to [annotationProcessors]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[annotationProcessors]"));
        assertTrue(config.contains("\"org.mapstruct:mapstruct-processor\" = \"1.6.3\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addAddsManagedTestProcessorDependencyWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "--managed",
                "test-processor",
                "io.micronaut:micronaut-inject-java");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added dependency io.micronaut:micronaut-inject-java with a platform-managed version to [test.annotationProcessors]"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[test.annotationProcessors]"));
        assertTrue(config.contains("\"io.micronaut:micronaut-inject-java\" = {}"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void addRejectsManagedDependencyWithExplicitVersion() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "--managed",
                "com.example:legacy-api:1.0.0");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains(
                "Managed dependency coordinate must not include a version. Use `group:artifact`."));
    }

    @Test
    void addRejectsUnknownDependencySectionWithSupportedSections() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "add",
                "--cwd", projectDir.toString(),
                "compile-only",
                "com.example:tool:1.0.0");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Unexpected dependency section `compile-only`"));
        assertTrue(result.stderr().contains("zolt add api group:artifact"));
        assertTrue(result.stderr().contains("zolt add runtime group:artifact"));
        assertTrue(result.stderr().contains("zolt add provided group:artifact"));
        assertTrue(result.stderr().contains("zolt add dev group:artifact"));
        assertTrue(result.stderr().contains("zolt add processor group:artifact"));
        assertTrue(result.stderr().contains("zolt add test-processor group:artifact"));
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
    void removeMissingDependencyPrintsFriendlyNoOpMessage() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "remove",
                "--cwd", projectDir.toString(),
                "com.example:missing");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Dependency com.example:missing is not present in [dependencies]; nothing to remove."));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void removeWithoutSectionDoesNotDeleteApiDependency() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                "central" = "https://repo.maven.apache.org/maven2"

                [platforms]

                [api.dependencies]
                "com.example:contract" = "1.0.0"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);

        CommandResult result = execute(
                "remove",
                "--cwd", projectDir.toString(),
                "com.example:contract");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Dependency com.example:contract is not present in [dependencies]; nothing to remove."));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("[api.dependencies]\n\"com.example:contract\" = \"1.0.0\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void removeDeletesApiDependency() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                "central" = "https://repo.maven.apache.org/maven2"

                [platforms]

                [api.dependencies]
                "com.example:contract" = "1.0.0"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """);

        CommandResult result = execute(
                "remove",
                "--cwd", projectDir.toString(),
                "api",
                "com.example:contract");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Removed dependency com.example:contract from [api.dependencies]"));
        assertTrue(result.stdout().contains("Resolved 0 packages"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertFalse(config.contains("\"com.example:contract\" = \"1.0.0\""));
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
    void platformAddWritesPlatformWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "platform",
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "com.example:enterprise-platform:2026.1.0");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "Added platform com.example:enterprise-platform:2026.1.0 to [platforms]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("\"com.example:enterprise-platform\" = \"2026.1.0\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
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
    void platformRemoveDeletesPlatformAndRefreshesLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        CommandResult add = execute(
                "platform",
                "add",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "com.example:enterprise-platform:2026.1.0");

        CommandResult remove = execute(
                "platform",
                "remove",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "com.example:enterprise-platform");

        assertEquals(0, add.exitCode());
        assertEquals(0, remove.exitCode());
        assertTrue(remove.stdout().contains("Removed platform com.example:enterprise-platform from [platforms]"));
        assertTrue(remove.stdout().contains("Resolved 0 packages"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertFalse(config.contains("\"com.example:enterprise-platform\""));
    }

    @Test
    void platformRemoveRejectsVersionedCoordinate() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "platform",
                "remove",
                "--cwd", projectDir.toString(),
                "com.example:enterprise-platform:2026.1.0");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains(
                "Platform remove coordinate must not include a version. Use `group:artifact`."));
    }

    @Test
    void treePrintsDependencyTreeFromProjectLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:app"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                dependencies = ["com.example:lib:1.0.0"]

                [[package]]
                id = "com.example:lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = false
                dependencies = []
                """);

        CommandResult result = execute("tree", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals("""
                com.example:demo:0.1.0
                \\- com.example:app:1.0.0
                   \\- com.example:lib:1.0.0
                """, result.stdout());
    }

    @Test
    void treePrintsJsonFromProjectLockfile() throws IOException {
        Path projectDir = tempDir.resolve("tree-json");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:app"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                dependencies = []

                [[policy]]
                kind = "global-exclusion"
                id = "commons-logging:commons-logging"
                requested = "1.2"
                source = "com.example:app:1.0.0"
                policy = "[dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)"
                """);

        CommandResult result = execute("tree", "--format", "json", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().startsWith("{\n  \"schemaVersion\": 1,"));
        assertTrue(result.stdout().contains("\"command\": \"tree\""));
        assertTrue(result.stdout().contains("\"roots\": [\"com.example:app:1.0.0\"]"));
        assertTrue(result.stdout().contains("\"policyEffects\": ["));
        assertTrue(result.stdout().contains("\"id\": \"commons-logging:commons-logging\""));
        assertEquals("", result.stderr());
    }

    @Test
    void treeReportsMissingLockfileCleanly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute("tree", "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.lock"));
    }

    @Test
    void whyPrintsPathFromProjectRootToPackage() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:app"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                dependencies = ["com.example:lib:1.0.0"]

                [[package]]
                id = "com.example:lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = false
                dependencies = []
                """);

        CommandResult result = execute("why", "--cwd", projectDir.toString(), "com.example:lib");

        assertEquals(0, result.exitCode());
        assertEquals("""
                com.example:demo:0.1.0
                \\- com.example:app:1.0.0
                   \\- com.example:lib:1.0.0
                """, result.stdout());
    }

    @Test
    void whyPrintsJsonForExcludedPackage() throws IOException {
        Path projectDir = tempDir.resolve("why-json");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:app"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                dependencies = []

                [[policy]]
                kind = "global-exclusion"
                id = "commons-logging:commons-logging"
                requested = "1.2"
                source = "com.example:app:1.0.0"
                policy = "[dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)"
                """);

        CommandResult result = execute(
                "why",
                "--format", "json",
                "--cwd", projectDir.toString(),
                "commons-logging:commons-logging");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"command\": \"why\""));
        assertTrue(result.stdout().contains("\"target\": \"commons-logging:commons-logging\""));
        assertTrue(result.stdout().contains("\"status\": \"excluded\""));
        assertTrue(result.stdout().contains("\"path\": []"));
        assertTrue(result.stdout().contains("\"policy\": \"[dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)\""));
        assertEquals("", result.stderr());
    }

    @Test
    void whyReportsMissingPackageClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute("why", "--cwd", projectDir.toString(), "com.example:missing");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Package com.example:missing is not present in zolt.lock"));
    }

    @Test
    void policyPrintsDependencyBaselineDiagnostics() throws IOException {
        Path projectDir = tempDir.resolve("policy-text");
        writePolicyProject(projectDir);
        writePolicyLockfile(projectDir);

        CommandResult result = execute("policy", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Dependency policy diagnostics"));
        assertTrue(result.stdout().contains("Platforms: 1"));
        assertTrue(result.stdout().contains("org.springframework.boot:spring-boot-dependencies:4.0.6 manages 1 selected packages"));
        assertTrue(result.stdout().contains("org.springframework.boot:spring-boot-starter-web:4.0.6 [compile] managed-version: org.springframework.boot:spring-boot-starter-web -> 4.0.6 from org.springframework.boot:spring-boot-dependencies:4.0.6"));
        assertTrue(result.stdout().contains("org.apache.tomcat.embed:tomcat-embed-core strict 10.1.40 status=pinned selected=10.1.40 source=org.springframework.boot:spring-boot-starter-web:4.0.6 reason=Container baseline"));
        assertTrue(result.stdout().contains("com.example:unused strict 1.0.0 status=unmatched"));
        assertTrue(result.stdout().contains("com.example:direct-lib status=direct-conflict reason=Direct dependency conflict fixture"));
        assertTrue(result.stdout().contains("commons-logging:commons-logging status=matched reason=Use jcl-over-slf4j"));
        assertTrue(result.stdout().contains("log4j:log4j status=unmatched reason=Legacy logging baseline"));
        assertTrue(result.stdout().contains("dependencies com.example:direct-lib:1.2.3 status=selected"));
        assertEquals("", result.stderr());
    }

    @Test
    void policyPrintsDeterministicJson() throws IOException {
        Path projectDir = tempDir.resolve("policy-json");
        writePolicyProject(projectDir);
        writePolicyLockfile(projectDir);

        CommandResult result = execute("policy", "--format", "json", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().startsWith("{\n  \"projectRoot\": \""));
        assertTrue(result.stdout().contains("\"platform\": \"org.springframework.boot:spring-boot-dependencies:4.0.6\""));
        assertTrue(result.stdout().contains("\"coordinate\": \"org.apache.tomcat.embed:tomcat-embed-core\""));
        assertTrue(result.stdout().contains("\"status\": \"pinned\""));
        assertTrue(result.stdout().contains("\"coordinate\": \"com.example:unused\""));
        assertTrue(result.stdout().contains("\"status\": \"unmatched\""));
        assertTrue(result.stdout().contains("\"coordinate\": \"commons-logging:commons-logging\""));
        assertTrue(result.stdout().contains("\"status\": \"matched\""));
        assertTrue(result.stdout().contains("\"status\": \"direct-conflict\""));
        assertTrue(result.stdout().contains("\"directVersions\": ["));
        assertTrue(result.stdout().contains("\"section\": \"dependencies\""));
        assertEquals(result.stdout(), execute("policy", "--format", "json", "--cwd", projectDir.toString()).stdout());
        assertEquals("", result.stderr());
    }

    @Test
    void conflictsPrintsConflictSummaryFromLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[conflict]]
                id = "org.slf4j:slf4j-api"
                selected = "2.0.16"
                requested = ["1.7.36", "2.0.16"]
                reason = "direct dependency wins"
                """);

        CommandResult result = execute("conflicts", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals("""
                Dependency conflicts:
                - org.slf4j:slf4j-api
                  selected: 2.0.16
                  requested: 1.7.36, 2.0.16
                  reason: direct dependency wins
                """, result.stdout());
    }

    @Test
    void conflictsExitsSuccessfullyWhenNoConflictsExist() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute("conflicts", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals("No dependency conflicts found.\n", result.stdout());
    }

    @Test
    void formattedCommandsFlushOutputBeforeExit() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        FlushTrackingPrintWriter stdout = new FlushTrackingPrintWriter();
        CommandLine commandLine = ZoltCli.newCommandLine();
        commandLine.setOut(stdout);
        commandLine.setErr(new PrintWriter(new StringWriter()));

        int exitCode = commandLine.execute("conflicts", "--cwd", projectDir.toString());

        assertEquals(0, exitCode);
        assertEquals("No dependency conflicts found.\n", stdout.content());
        assertTrue(stdout.flushed());
    }

    @Test
    void classpathPrintsRequestedClasspathFromLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:compile-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "com/example/compile-lib/1.0.0/compile-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:test-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "com/example/test-lib/1.0.0/test-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:processor-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "processor"
                direct = true
                jar = "com/example/processor-lib/1.0.0/processor-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:test-processor-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "test-processor"
                direct = true
                jar = "com/example/test-processor-lib/1.0.0/test-processor-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "io.quarkus:quarkus-rest-deployment"
                version = "3.33.0"
                source = "maven-central"
                scope = "quarkus-deployment"
                direct = false
                jar = "io/quarkus/quarkus-rest-deployment/3.33.0/quarkus-rest-deployment-3.33.0.jar"
                dependencies = []
                """);

        Path compileJar = cacheRoot.resolve("com/example/compile-lib/1.0.0/compile-lib-1.0.0.jar");
        Path runtimeJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Path testJar = cacheRoot.resolve("com/example/test-lib/1.0.0/test-lib-1.0.0.jar");
        Path processorJar = cacheRoot.resolve("com/example/processor-lib/1.0.0/processor-lib-1.0.0.jar");
        Path testProcessorJar = cacheRoot.resolve(
                "com/example/test-processor-lib/1.0.0/test-processor-lib-1.0.0.jar");
        Path quarkusDeploymentJar = cacheRoot.resolve(
                "io/quarkus/quarkus-rest-deployment/3.33.0/quarkus-rest-deployment-3.33.0.jar");

        CommandResult compile = execute(
                "classpath",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "compile");
        CommandResult runtime = execute(
                "classpath",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "runtime");
        CommandResult test = execute(
                "classpath",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "test");
        CommandResult processor = execute(
                "classpath",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "processor");
        CommandResult testProcessor = execute(
                "classpath",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "test-processor");
        CommandResult quarkusDeployment = execute(
                "classpath",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "quarkus-deployment");

        assertEquals(0, compile.exitCode());
        assertEquals(compileJar + System.lineSeparator(), compile.stdout());
        assertEquals(0, runtime.exitCode());
        assertEquals(
                compileJar + File.pathSeparator + runtimeJar + System.lineSeparator(),
                runtime.stdout());
        assertEquals(0, test.exitCode());
        assertEquals(
                compileJar + File.pathSeparator + runtimeJar + File.pathSeparator + testJar + System.lineSeparator(),
                test.stdout());
        assertEquals(0, processor.exitCode());
        assertEquals(processorJar + System.lineSeparator(), processor.stdout());
        assertEquals(0, testProcessor.exitCode());
        assertEquals(testProcessorJar + System.lineSeparator(), testProcessor.stdout());
        assertEquals(0, quarkusDeployment.exitCode());
        assertEquals(quarkusDeploymentJar + System.lineSeparator(), quarkusDeployment.stdout());
    }

    @Test
    void classpathRejectsUnknownKindWithSupportedKinds() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute("classpath", "--cwd", projectDir.toString(), "processor-test");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Unknown classpath kind `processor-test`"));
        assertTrue(result.stderr().contains("compile, runtime, test, processor, test-processor, quarkus-deployment, or audit"));
    }

    @Test
    void classpathAuditPrintsLanePolicyAndResolvedPackages() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "jakarta.servlet:jakarta.servlet-api"
                version = "6.1.0"
                source = "maven-central"
                scope = "provided"
                direct = true
                jar = "jakarta/servlet/jakarta.servlet-api/6.1.0/jakarta.servlet-api-6.1.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:devtools"
                version = "1.0.0"
                source = "maven-central"
                scope = "dev"
                direct = true
                jar = "com/example/devtools/1.0.0/devtools-1.0.0.jar"
                dependencies = []
                """);

        CommandResult result = execute("classpath", "--cwd", projectDir.toString(), "audit");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Classpath lane audit"));
        assertTrue(result.stdout().contains("provided            yes     no      no   no        no             no              provided-container"));
        assertTrue(result.stdout().contains("- com.example:devtools:1.0.0 [dev] lanes=runtime,test package=development-only"));
        assertTrue(result.stdout().contains("- jakarta.servlet:jakarta.servlet-api:6.1.0 [provided] lanes=compile package=provided-container"));
        assertEquals("", result.stderr());
    }

    @Test
    void classpathAuditPrintsJsonForTooling() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "jakarta.servlet:jakarta.servlet-api"
                version = "6.1.0"
                source = "maven-central"
                scope = "provided"
                direct = true
                jar = "jakarta/servlet/jakarta.servlet-api/6.1.0/jakarta.servlet-api-6.1.0.jar"
                dependencies = []
                """);

        CommandResult result = execute("classpath", "--cwd", projectDir.toString(), "audit", "--format", "json");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"command\": \"classpath audit\""));
        assertTrue(result.stdout().contains("\"scope\": \"provided\""));
        assertTrue(result.stdout().contains("\"lanes\": [\"compile\"]"));
        assertTrue(result.stdout().contains("\"disposition\": \"provided-container\""));
        assertEquals("", result.stderr());
    }

    @Test
    void classpathJsonFormatIsOnlyForAudit() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute("classpath", "--cwd", projectDir.toString(), "compile", "--format", "json");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Use `zolt classpath audit --format json`."));
    }

    @Test
    void classpathReportsMissingLockfileCleanly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);

        CommandResult result = execute("classpath", "--cwd", projectDir.toString(), "runtime");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.lock"));
    }

    @Test
    void quarkusPlanPrintsAugmentationInputsFromLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        enableQuarkus(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "io.quarkus:quarkus-rest"
                version = "3.33.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar"
                dependencies = []

                [[package]]
                id = "io.quarkus:quarkus-rest-deployment"
                version = "3.33.0"
                source = "maven-central"
                scope = "quarkus-deployment"
                direct = false
                jar = "io/quarkus/quarkus-rest-deployment/3.33.0/quarkus-rest-deployment-3.33.0.jar"
                dependencies = []
                """);
        Path root = projectDir.toAbsolutePath().normalize();
        Path runtimeJar = cacheRoot.resolve("io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar");
        Path deploymentJar = cacheRoot.resolve(
                "io/quarkus/quarkus-rest-deployment/3.33.0/quarkus-rest-deployment-3.33.0.jar");
        createJarWithTextEntry(
                runtimeJar,
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0\n");

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        String inputFingerprint = quarkusInputFingerprint(result.stdout());
        assertTrue(inputFingerprint.matches("sha256:[0-9a-f]{64}"));
        assertEquals("""
                Quarkus augmentation plan
                Status: inputs resolved; augmentation runner not implemented yet
                Application classes: %s
                Package target: fast-jar
                Augmentation output: %s
                Package output: %s
                Input fingerprint: %s
                Augmentation metadata: missing (%s)
                Runtime classpath entries: 1
                  %s
                Deployment classpath entries: 1
                  %s
                Quarkus extensions: 1
                  io.quarkus:quarkus-rest -> io.quarkus:quarkus-rest-deployment:3.33.0
                    runtime jar: %s
                    deployment jar: %s
                Next: implement the Zolt-owned Quarkus augmentation runner with these inputs.
                """.formatted(
                root.resolve("target/classes"),
                root.resolve("target/quarkus"),
                root.resolve("target/quarkus-app"),
                inputFingerprint,
                root.resolve("target/quarkus/zolt-augmentation.properties"),
                runtimeJar,
                deploymentJar,
                runtimeJar,
                deploymentJar), result.stdout());
    }

    @Test
    void quarkusPlanFailsWhenFrameworkIsNotEnabled() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(1, result.exitCode());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().contains("Quarkus is not enabled for this project"));
        assertTrue(result.stderr().contains("[framework.quarkus] enabled = true"));
    }

    @Test
    void quarkusTestPlanReportsPlainJUnitStatus() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        enableQuarkus(projectDir);
        Path testClass = projectDir.resolve("target/test-classes/com/example/PlainTest.class");
        Files.createDirectories(testClass.getParent());
        Files.writeString(testClass, "constant-pool:plain-junit");

        CommandResult result = execute(
                "quarkus",
                "test-plan",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Quarkus test plan"));
        assertTrue(result.stdout().contains("Status: plain JUnit tests can run through the current Zolt test runner"));
        assertTrue(result.stdout().contains("Compiled test output: present"));
        assertTrue(result.stdout().contains("Unsupported Quarkus tests: 0"));
    }

    @Test
    void quarkusTestPlanReportsUnsupportedQuarkusTests() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        enableQuarkus(projectDir);
        Path testClass = projectDir.resolve("target/test-classes/com/example/HttpTest.class");
        Files.createDirectories(testClass.getParent());
        Files.writeString(testClass, "constant-pool:Lio/quarkus/test/junit/QuarkusTest;");

        CommandResult result = execute(
                "quarkus",
                "test-plan",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Status: blocked by unsupported Quarkus test annotations"));
        assertTrue(result.stdout().contains("Unsupported Quarkus tests: 1"));
        assertTrue(result.stdout().contains("com/example/HttpTest.class (@QuarkusTest)"));
    }

    @Test
    void quarkusTestPlanFailsWhenFrameworkIsNotEnabled() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "quarkus",
                "test-plan",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().contains("Quarkus is not enabled for this project"));
        assertTrue(result.stderr().contains("[framework.quarkus] enabled = true"));
    }

    @Test
    void quarkusPlanReportsCurrentAugmentationMetadata() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        enableQuarkus(projectDir);
        writeQuarkusPlanLockfile(projectDir);
        Path runtimeJar = cacheRoot.resolve("io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar");
        createJarWithTextEntry(
                runtimeJar,
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0\n");
        String fingerprint = quarkusInputFingerprint(execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString()).stdout());
        writeQuarkusAugmentationMetadata(projectDir, fingerprint);

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Augmentation metadata: current"));
        assertTrue(result.stdout().contains("recorded " + fingerprint));
    }

    @Test
    void quarkusPlanReportsStaleAugmentationMetadata() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        enableQuarkus(projectDir);
        writeQuarkusPlanLockfile(projectDir);
        Path runtimeJar = cacheRoot.resolve("io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar");
        createJarWithTextEntry(
                runtimeJar,
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0\n");
        writeQuarkusAugmentationMetadata(projectDir, "sha256:" + "0".repeat(64));

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Augmentation metadata: stale"));
        assertTrue(result.stdout().contains("recorded sha256:" + "0".repeat(64)));
    }

    @Test
    void quarkusPlanFailsWhenNoDeploymentInputsAreResolved() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        enableQuarkus(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:app"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "com/example/app/1.0.0/app-1.0.0.jar"
                dependencies = []
                """);

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("Status: not ready"));
        assertTrue(result.stdout().contains("Deployment classpath entries: 0"));
        assertTrue(result.stderr().contains("No Quarkus deployment artifacts were found in zolt.lock"));
        assertTrue(result.stderr().contains("run `zolt resolve`"));
    }

    @Test
    void quarkusPlanFailsWhenRuntimeExtensionDeploymentIsMissingFromLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        enableQuarkus(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "io.quarkus:quarkus-rest"
                version = "3.33.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar"
                dependencies = []

                [[package]]
                id = "io.quarkus:quarkus-arc-deployment"
                version = "3.33.0"
                source = "maven-central"
                scope = "quarkus-deployment"
                direct = false
                jar = "io/quarkus/quarkus-arc-deployment/3.33.0/quarkus-arc-deployment-3.33.0.jar"
                dependencies = []
                """);
        Path runtimeJar = cacheRoot.resolve("io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar");
        createJarWithTextEntry(
                runtimeJar,
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0\n");

        CommandResult result = execute(
                "quarkus",
                "plan",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("io.quarkus:quarkus-rest -> io.quarkus:quarkus-rest-deployment:3.33.0"));
        assertTrue(result.stdout().contains("deployment jar: missing from zolt.lock"));
        assertTrue(result.stderr().contains("matching deployment artifacts"));
        assertTrue(result.stderr().contains("Run `zolt resolve`"));
    }

    @Test
    void ideModelPrintsDeterministicJsonFromProjectAndLockfile() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:app"
                version = "1.0.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "com/example/app/1.0.0/app-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:test-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "com/example/test-lib/1.0.0/test-lib-1.0.0.jar"
                dependencies = []
                """);

        CommandResult result = execute(
                "ide",
                "model",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "--format", "json");

        Path root = projectDir.toAbsolutePath().normalize();
        Path appJar = cacheRoot.toAbsolutePath().normalize().resolve("com/example/app/1.0.0/app-1.0.0.jar");
        Path testJar = cacheRoot.toAbsolutePath().normalize().resolve("com/example/test-lib/1.0.0/test-lib-1.0.0.jar");
        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertEquals("""
                {
                  "schemaVersion": 1,
                  "project": {
                    "name": "demo",
                    "group": "com.example",
                    "version": "0.1.0",
                    "mainClass": "com.example.Main"
                  },
                  "java": {
                    "version": "%s",
                    "detectedVersion": null,
                    "javaHome": null
                  },
                  "compiler": {
                    "release": null,
                    "effectiveRelease": "%s",
                    "encoding": null,
                    "args": [],
                    "testArgs": [],
                    "generatedSources": "%s",
                    "generatedTestSources": "%s"
                  },
                  "testRuntime": {
                    "jvmArgs": [],
                    "systemProperties": {},
                    "environment": {},
                    "events": []
                  },
                  "package": {
                    "mode": "thin",
                    "sources": false,
                    "javadoc": false,
                    "tests": false,
                    "mainJar": "%s",
                    "sourcesJar": null,
                    "javadocJar": null,
                    "testsJar": null,
                    "metadata": {
                      "name": null,
                      "description": null,
                      "url": null,
                      "license": null,
                      "developers": [],
                      "scm": null,
                      "issues": null
                    },
                    "manifestAttributes": {}
                  },
                  "paths": {
                    "root": "%s",
                    "config": "%s",
                    "lockfile": "%s"
                  },
                  "sourceRoots": [
                    {
                      "id": "main-java",
                      "kind": "main",
                      "language": "java",
                      "path": "%s",
                      "generated": false
                    },
                    {
                      "id": "main-generated-java",
                      "kind": "main",
                      "language": "java",
                      "path": "%s",
                      "generated": true
                    },
                    {
                      "id": "test-java-1",
                      "kind": "test",
                      "language": "java",
                      "path": "%s",
                      "generated": false
                    },
                    {
                      "id": "test-generated-java",
                      "kind": "test",
                      "language": "java",
                      "path": "%s",
                      "generated": true
                    }
                  ],
                  "generatedSources": [],
                  "resourceRoots": [
                    {
                      "id": "main-resources",
                      "kind": "main",
                      "path": "%s"
                    },
                    {
                      "id": "test-resources",
                      "kind": "test",
                      "path": "%s"
                    }
                  ],
                  "outputs": {
                    "mainClasses": "%s",
                    "testClasses": "%s",
                    "package": "%s"
                  },
                  "dependencies": {
                    "api": [],
                    "implementation": [],
                    "runtime": [],
                    "provided": [],
                    "dev": [],
                    "test": [],
                    "annotationProcessors": [],
                    "testAnnotationProcessors": []
                  },
                  "classpaths": {
                    "compile": [
                      "%s"
                    ],
                    "runtime": [
                      "%s",
                      "%s"
                    ],
                    "test": [
                      "%s",
                      "%s",
                      "%s",
                      "%s"
                    ],
                    "processor": [],
                    "testProcessor": [],
                    "quarkusDeployment": []
                  },
                  "frameworks": {
                    "quarkus": {
                      "enabled": false,
                      "packageMode": null,
                      "augmentationStatus": "disabled",
                      "inputFingerprint": null,
                      "recordedInputFingerprint": null,
                      "augmentationMetadata": null,
                      "augmentationDirectory": null,
                      "packageDirectory": null,
                      "runnerJar": null,
                      "generatedBytecodeJar": null,
                      "transformedBytecodeJar": null,
                      "deploymentClasspath": []
                    }
                  },
                  "diagnostics": []
                }
                """.formatted(
                currentJavaMajorVersion(),
                currentJavaMajorVersion(),
                jsonPath(root.resolve("target/generated/sources/annotations")),
                jsonPath(root.resolve("target/generated/test-sources/annotations")),
                jsonPath(root.resolve("target/demo-0.1.0.jar")),
                jsonPath(root),
                jsonPath(root.resolve("zolt.toml")),
                jsonPath(root.resolve("zolt.lock")),
                jsonPath(root.resolve("src/main/java")),
                jsonPath(root.resolve("target/generated/sources/annotations")),
                jsonPath(root.resolve("src/test/java")),
                jsonPath(root.resolve("target/generated/test-sources/annotations")),
                jsonPath(root.resolve("src/main/resources")),
                jsonPath(root.resolve("src/test/resources")),
                jsonPath(root.resolve("target/classes")),
                jsonPath(root.resolve("target/test-classes")),
                jsonPath(root.resolve("target/demo-0.1.0.jar")),
                jsonPath(appJar),
                jsonPath(root.resolve("target/classes")),
                jsonPath(appJar),
                jsonPath(root.resolve("target/classes")),
                jsonPath(root.resolve("target/test-classes")),
                jsonPath(appJar),
                jsonPath(testJar)), result.stdout());
    }

    @Test
    void ideModelReportsMissingLockfileAsJsonDiagnostic() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "ide",
                "model",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\"code\": \"LOCKFILE_MISSING\""));
        assertTrue(result.stdout().contains("\"nextStep\": \"Run zolt resolve.\""));
        assertTrue(result.stdout().contains("\"compile\": []"));
        assertTrue(result.stdout().contains("\"runtime\": []"));
        assertTrue(result.stdout().contains("\"test\": []"));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void ideModelReportsUnreadableLockfileAsJsonDiagnostic() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), "this is not toml");

        CommandResult result = execute(
                "ide",
                "model",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\"code\": \"LOCKFILE_UNREADABLE\""));
        assertTrue(result.stdout().contains("Could not parse zolt.lock"));
        assertTrue(result.stdout().contains("\"nextStep\": \"Run zolt resolve.\""));
        assertTrue(result.stdout().contains("\"compile\": []"));
        assertTrue(result.stdout().contains("\"runtime\": []"));
        assertTrue(result.stdout().contains("\"test\": []"));
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
    void ideModelCheckLockReportsStaleLockfileWithoutRewritingIt() throws IOException {
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
                    "--check-lock",
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
    void ideModelCheckLockReportsMissingLockfileAsJsonDiagnostic() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "ide",
                "model",
                "--check-lock",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\"code\": \"LOCKFILE_MISSING\""));
        assertTrue(result.stdout().contains("\"nextStep\": \"Run zolt resolve.\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void ideModelCheckLockOfflineReportsUnavailableCacheAsJsonDiagnostic() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(
                projectDir,
                "https://repo.maven.apache.org/maven2",
                Map.of("com.example:missing", "1.0.0"),
                Map.of());
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "ide",
                "model",
                "--check-lock",
                "--offline",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString(),
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\"code\": \"LOCKFILE_CHECK_UNAVAILABLE\""));
        assertTrue(result.stdout().contains("Offline mode requires cached POM"));
        assertTrue(result.stdout().contains("retry zolt ide model --check-lock --offline"));
        assertEquals("version = 1\n", Files.readString(projectDir.resolve("zolt.lock")));
    }

    @Test
    void ideModelWorkspacePrintsWorkspaceJson() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                defaultMembers = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));
        Files.writeString(apiDir.resolve("zolt.lock"), "version = 1\n");
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Files.writeString(coreDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "ide",
                "model",
                "--workspace",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--format", "json");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\"workspace\": {"));
        assertTrue(result.stdout().contains("\"name\": \"workspace\""));
        assertTrue(result.stdout().contains("\"members\": ["));
        assertTrue(result.stdout().contains("\"apps/api\""));
        assertTrue(result.stdout().contains("\"modules/core\""));
        assertTrue(result.stdout().contains("\"projects\": ["));
        assertTrue(result.stdout().contains("\"member\": \"apps/api\""));
        assertTrue(result.stdout().contains("\"member\": \"modules/core\""));
        assertTrue(result.stdout().contains("\"edges\": []"));
        assertTrue(result.stdout().contains("\"diagnostics\": []"));
    }

    @Test
    void resolveWorkspaceWritesRootLockfile() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                defaultMembers = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));

        CommandResult result = execute(
                "resolve",
                "--workspace",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Resolved 0 packages"));
        assertTrue(result.stdout().contains("Wrote " + workspaceDir.resolve("zolt.lock")));
        assertEquals("version = 1\n\n", Files.readString(workspaceDir.resolve("zolt.lock")));
        assertFalse(Files.exists(apiDir.resolve("zolt.lock")));
        assertFalse(Files.exists(coreDir.resolve("zolt.lock")));
    }

    @Test
    void buildResolvesMissingLockfileAndCompilesMainSources() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """);

        CommandResult result = execute(
                "build",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Resolved dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("Compiled 1 main source files"));
        assertTrue(result.stdout().contains("Wrote classes to " + projectDir.resolve("target/classes")));
        assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
        assertTrue(Files.exists(projectDir.resolve("target/classes/com/example/Main.class")));
    }

    @Test
    void buildRunsQuarkusAugmentationWhenFrameworkIsEnabled() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        enableQuarkus(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "build",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("Compiled 0 main source files"));
        assertTrue(result.stdout().contains("Wrote classes to " + projectDir.resolve("target/classes")));
        assertTrue(result.stderr().contains("No Quarkus deployment artifacts were found in zolt.lock"));
        assertTrue(result.stderr().contains("run `zolt resolve`"));
    }

    @Test
    void buildWorkspaceCompilesMembersInDependencyOrder() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);

        CommandResult result = execute(
                "build",
                "--workspace",
                "--all",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Resolved workspace dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in modules/core"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in apps/api"));
        assertTrue(result.stdout().contains("Compiled 2 workspace main source files"));
        assertTrue(Files.exists(coreDir.resolve("target/classes/com/example/core/Core.class")));
        assertTrue(Files.exists(apiDir.resolve("target/classes/com/example/api/Api.class")));
    }

    @Test
    void buildWorkspacePrintsNestedJsonTimingsWhenRequested() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);

        CommandResult result = execute(
                "build",
                "--workspace",
                "--all",
                "--timings",
                "--timings-format", "json",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Compiled 2 workspace main source files"));
        String[] lines = result.stderr().lines().toArray(String[]::new);
        assertEquals(3, lines.length);
        assertTrue(lines[0].contains("\"phase\":\"plan workspace build\""));
        assertTrue(lines[0].contains("\"depth\":1"));
        assertTrue(lines[0].contains("\"includedMembers\":\"2\""));
        assertTrue(lines[0].contains("\"selectedMembers\":\"2\""));
        assertTrue(lines[0].contains("\"resolvedLockfile\":\"true\""));
        assertTrue(lines[1].contains("\"phase\":\"compile workspace members\""));
        assertTrue(lines[1].contains("\"depth\":1"));
        assertTrue(lines[1].contains("\"members\":\"2\""));
        assertTrue(lines[1].contains("\"sourceFiles\":\"2\""));
        assertTrue(lines[1].contains("\"mainCompilationsSkipped\":\"0\""));
        assertTrue(lines[1].contains("\"mainCompilationsExecuted\":\"2\""));
        assertTrue(lines[2].contains("\"phase\":\"build workspace\""));
        assertTrue(lines[2].contains("\"depth\":0"));
        assertTrue(lines[2].contains("\"members\":\"2\""));
        assertTrue(lines[2].contains("\"sourceFiles\":\"2\""));
        assertTrue(lines[2].contains("\"mainCompilationsSkipped\":\"0\""));
        assertTrue(lines[2].contains("\"mainCompilationsExecuted\":\"2\""));
    }

    @Test
    void buildWorkspaceMemberSelectionCompilesDependenciesOnly() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Path workerDir = workspaceDir.resolve("apps/worker");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.createDirectories(workerDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core", "apps/worker"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);
        Files.writeString(workerDir.resolve("zolt.toml"), memberConfig("worker"));
        Path workerSource = workerDir.resolve("src/main/java/com/example/worker/Worker.java");
        Files.createDirectories(workerSource.getParent());
        Files.writeString(workerSource, """
                package com.example.worker;

                public final class Worker {
                }
                """);

        CommandResult result = execute(
                "build",
                "--workspace",
                "--member", "apps/api",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Compiled 1 main source files in modules/core"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in apps/api"));
        assertFalse(result.stdout().contains("apps/worker"));
        assertTrue(Files.exists(coreDir.resolve("target/classes/com/example/core/Core.class")));
        assertTrue(Files.exists(apiDir.resolve("target/classes/com/example/api/Api.class")));
        assertFalse(Files.exists(workerDir.resolve("target/classes/com/example/worker/Worker.class")));
    }

    @Test
    void buildWorkspaceMembersOptionSelectsCommaSeparatedMembers() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Path workerDir = workspaceDir.resolve("apps/worker");
        Path adminDir = workspaceDir.resolve("apps/admin");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.createDirectories(workerDir);
        Files.createDirectories(adminDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core", "apps/worker", "apps/admin"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);
        Files.writeString(workerDir.resolve("zolt.toml"), memberConfig("worker"));
        Path workerSource = workerDir.resolve("src/main/java/com/example/worker/Worker.java");
        Files.createDirectories(workerSource.getParent());
        Files.writeString(workerSource, """
                package com.example.worker;

                public final class Worker {
                }
                """);
        Files.writeString(adminDir.resolve("zolt.toml"), memberConfig("admin"));
        Path adminSource = adminDir.resolve("src/main/java/com/example/admin/Admin.java");
        Files.createDirectories(adminSource.getParent());
        Files.writeString(adminSource, """
                package com.example.admin;

                public final class Admin {
                }
                """);

        CommandResult result = execute(
                "build",
                "--workspace",
                "--members", "apps/api,apps/worker",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Compiled 1 main source files in modules/core"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in apps/api"));
        assertTrue(result.stdout().contains("Compiled 1 main source files in apps/worker"));
        assertFalse(result.stdout().contains("apps/admin"));
        assertTrue(Files.exists(coreDir.resolve("target/classes/com/example/core/Core.class")));
        assertTrue(Files.exists(apiDir.resolve("target/classes/com/example/api/Api.class")));
        assertTrue(Files.exists(workerDir.resolve("target/classes/com/example/worker/Worker.class")));
        assertFalse(Files.exists(adminDir.resolve("target/classes/com/example/admin/Admin.class")));
    }

    @Test
    void workspaceMembersOptionConflictsWithAll() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Files.createDirectories(apiDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api"]
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api"));

        CommandResult result = execute(
                "build",
                "--workspace",
                "--all",
                "--members", "apps/api",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Use either --all or member selection for workspace selection, not both."));
    }

    @Test
    void testWorkspaceRunsMembersInDependencyOrder() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Path cacheRoot = tempDir.resolve("cache");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        writeFakeConsoleJar(cacheRoot.resolve(
                "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"));
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);
        Path apiTest = apiDir.resolve("src/test/java/com/example/api/ApiTest.java");
        Files.createDirectories(apiTest.getParent());
        Files.writeString(apiTest, """
                package com.example.api;

                import com.example.core.Core;

                public final class ApiTest {
                    public String message() {
                        return Api.message() + Core.message();
                    }
                }
                """);
        Files.writeString(workspaceDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:core"
                version = "0.1.0"
                source = "workspace"
                scope = "compile"
                direct = true
                workspace = "modules/core"
                workspaceOutput = "target/classes"
                dependencies = []

                [[package]]
                id = "org.junit.platform:junit-platform-console-standalone"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
                dependencies = []
                """);

        CommandResult result = execute(
                "test",
                "--workspace",
                "--all",
                "--reports-dir", "target/test-reports",
                "--timings",
                "--timings-format", "json",
                "--cwd", apiDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("fake console"));
        assertTrue(result.stdout().contains("Tests passed in modules/core"));
        assertTrue(result.stdout().contains("Wrote test reports for modules/core to "));
        assertTrue(result.stdout().contains("Tests passed in apps/api"));
        assertTrue(result.stdout().contains("Wrote test reports for apps/api to "));
        assertTrue(result.stdout().contains("Tests passed for 2 workspace members"));
        assertTrue(Files.exists(coreDir.resolve("target/test-reports/modules/core/TEST-fake-console.xml")));
        assertTrue(Files.exists(apiDir.resolve("target/test-reports/apps/api/TEST-fake-console.xml")));
        String[] lines = result.stderr().lines().toArray(String[]::new);
        assertEquals(4, lines.length);
        assertTrue(lines[0].contains("\"phase\":\"plan workspace tests\""));
        assertTrue(lines[0].contains("\"depth\":1"));
        assertTrue(lines[0].contains("\"includedMembers\":\"2\""));
        assertTrue(lines[0].contains("\"selectedMembers\":\"2\""));
        assertTrue(lines[1].contains("\"phase\":\"build workspace test inputs\""));
        assertTrue(lines[1].contains("\"depth\":1"));
        assertTrue(lines[1].contains("\"members\":\"2\""));
        assertTrue(lines[1].contains("\"sourceFiles\":\"2\""));
        assertTrue(lines[1].contains("\"mainCompilationsSkipped\":\"0\""));
        assertTrue(lines[1].contains("\"mainCompilationsExecuted\":\"2\""));
        assertTrue(lines[2].contains("\"phase\":\"run workspace test members\""));
        assertTrue(lines[2].contains("\"depth\":1"));
        assertTrue(lines[2].contains("\"members\":\"2\""));
        assertTrue(lines[2].contains("\"testSourceFiles\":\"1\""));
        assertTrue(lines[2].contains("\"mainCompilationsSkipped\":\"0\""));
        assertTrue(lines[2].contains("\"mainCompilationsExecuted\":\"2\""));
        assertTrue(lines[2].contains("\"testCompilationsSkipped\""));
        assertTrue(lines[2].contains("\"testCompilationsExecuted\""));
        assertTrue(lines[2].contains("\"testDiscoveryScanRoots\""));
        assertTrue(lines[3].contains("\"phase\":\"test workspace\""));
        assertTrue(lines[3].contains("\"depth\":0"));
        assertTrue(lines[3].contains("\"members\":\"2\""));
        assertTrue(lines[3].contains("\"testSourceFiles\":\"1\""));
        assertTrue(lines[3].contains("\"mainCompilationsSkipped\":\"0\""));
        assertTrue(lines[3].contains("\"mainCompilationsExecuted\":\"2\""));
        assertTrue(lines[3].contains("\"testCompilationsSkipped\""));
        assertTrue(lines[3].contains("\"testCompilationsExecuted\""));
        assertTrue(lines[3].contains("\"testDiscoveryScanRoots\""));
        assertTrue(Files.exists(coreDir.resolve("target/classes/com/example/core/Core.class")));
        assertTrue(Files.exists(apiDir.resolve("target/classes/com/example/api/Api.class")));
        assertTrue(Files.exists(apiDir.resolve("target/test-classes/com/example/api/ApiTest.class")));
    }

    @Test
    void testWorkspaceMemberAppliesSelectedTestPattern() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace-selected-tests");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Path cacheRoot = tempDir.resolve("cache-selected-tests");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        writeFakeConsoleJar(cacheRoot.resolve(
                "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"));
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    public static String message() {
                        return Core.message();
                    }
                }
                """);
        Path apiTest = apiDir.resolve("src/test/java/com/example/api/ApiTest.java");
        Files.createDirectories(apiTest.getParent());
        Files.writeString(apiTest, """
                package com.example.api;

                public final class ApiTest {
                    public String message() {
                        return Api.message();
                    }
                }
                """);
        Files.writeString(workspaceDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:core"
                version = "0.1.0"
                source = "workspace"
                scope = "compile"
                direct = true
                workspace = "modules/core"
                workspaceOutput = "target/classes"
                dependencies = []

                [[package]]
                id = "org.junit.platform:junit-platform-console-standalone"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
                dependencies = []
                """);

        CommandResult result = execute(
                "test",
                "--workspace",
                "--member", "apps/api",
                "--tests", "*ApiTest",
                "--timings",
                "--timings-format", "json",
                "--cwd", apiDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("fake console"));
        assertTrue(result.stdout().contains("Tests passed in apps/api"));
        assertFalse(result.stdout().contains("Tests passed in modules/core"));
        String[] lines = result.stderr().lines().toArray(String[]::new);
        assertEquals(4, lines.length);
        assertTrue(lines[0].contains("\"selectedMembers\":\"1\""));
        assertTrue(lines[2].contains("\"phase\":\"run workspace test members\""));
        assertTrue(lines[2].contains("\"testPatterns\":\"1\""));
        assertTrue(lines[3].contains("\"phase\":\"test workspace\""));
        assertTrue(lines[3].contains("\"members\":\"1\""));
        assertTrue(lines[3].contains("\"testPatterns\":\"1\""));
        assertTrue(Files.exists(coreDir.resolve("target/classes/com/example/core/Core.class")));
        assertTrue(Files.exists(apiDir.resolve("target/test-classes/com/example/api/ApiTest.class")));
    }

    @Test
    void testCommandWritesJUnitReportsWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("reports-demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeFakeConsoleJar(cacheRoot.resolve(
                "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"));
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("reports-demo"));
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.junit.platform:junit-platform-console-standalone"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
                dependencies = []
                """);
        Path testSource = projectDir.resolve("src/test/java/com/example/DemoTest.java");
        Files.createDirectories(testSource.getParent());
        Files.writeString(testSource, "package com.example; public final class DemoTest {}\n");

        CommandResult result = execute(
                "test",
                "--reports-dir", "target/test-reports",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        Path report = projectDir.resolve("target/test-reports/TEST-fake-console.xml");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("fake console"));
        assertTrue(result.stdout().contains("Tests passed"));
        assertTrue(result.stdout().contains("Wrote test reports to "
                + projectDir.resolve("target/test-reports").toAbsolutePath().normalize()));
        assertTrue(Files.exists(report));
        assertTrue(Files.readString(report).contains("testsuite"));
    }

    @Test
    void testCommandPrintsRequestedEventOutput() throws IOException {
        Path projectDir = tempDir.resolve("events-demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeFakeConsoleJar(cacheRoot.resolve(
                "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"));
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("events-demo"));
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.junit.platform:junit-platform-console-standalone"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
                dependencies = []
                """);
        Path testSource = projectDir.resolve("src/test/java/com/example/DemoTest.java");
        Files.createDirectories(testSource.getParent());
        Files.writeString(testSource, "package com.example; public final class DemoTest {}\n");

        CommandResult result = execute(
                "test",
                "--test-event", "failed",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("fake console"));
        assertTrue(result.stdout().contains("fake console event output"));
        assertTrue(result.stdout().contains("Tests passed"));
    }

    @Test
    void testCommandPrintsNestedJsonTimingsWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeFakeConsoleJar(cacheRoot.resolve(
                "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"));
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo"));
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.junit.platform:junit-platform-console-standalone"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
                dependencies = []
                """);
        Path testSource = projectDir.resolve("src/test/java/com/example/DemoTest.java");
        Files.createDirectories(testSource.getParent());
        Files.writeString(testSource, "package com.example; public final class DemoTest {}\n");

        CommandResult result = execute(
                "test",
                "--tests", "*DemoTest",
                "--include-tag", "fast",
                "--exclude-tag", "slow",
                "--timings",
                "--timings-format", "json",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("fake console"));
        assertTrue(result.stdout().contains("Tests passed"));
        String[] lines = result.stderr().lines().toArray(String[]::new);
        assertEquals(6, lines.length);
        assertTrue(lines[0].contains("\"phase\":\"config read\""));
        assertTrue(lines[0].contains("\"depth\":0"));
        assertTrue(lines[1].contains("\"phase\":\"build test inputs\""));
        assertTrue(lines[1].contains("\"depth\":2"));
        assertTrue(lines[1].contains("\"mainCompilationSkipped\""));
        assertTrue(lines[2].contains("\"phase\":\"compile test sources\""));
        assertTrue(lines[2].contains("\"depth\":2"));
        assertTrue(lines[2].contains("\"testSourceFiles\":\"1\""));
        assertTrue(lines[2].contains("\"testCompilationSkipped\":\"false\""));
        assertTrue(lines[3].contains("\"phase\":\"compile tests\""));
        assertTrue(lines[3].contains("\"depth\":1"));
        assertTrue(lines[3].contains("\"testSourceFiles\":\"1\""));
        assertTrue(lines[3].contains("\"testCompilationSkipped\":\"false\""));
        assertTrue(lines[4].contains("\"phase\":\"execute tests\""));
        assertTrue(lines[4].contains("\"depth\":1"));
        assertTrue(lines[4].contains("\"testRunner\":\"junit-console\""));
        assertTrue(lines[4].contains("\"testRuntimeClasspathEntries\""));
        assertTrue(lines[4].contains("\"testLauncherClasspathEntries\""));
        assertTrue(lines[4].contains("\"testDiscoveryScanRoots\""));
        assertTrue(lines[4].contains("\"testPatterns\":\"1\""));
        assertTrue(lines[4].contains("\"testIncludedTags\":\"1\""));
        assertTrue(lines[4].contains("\"testExcludedTags\":\"1\""));
        assertTrue(lines[4].contains("\"outputBytes\""));
        assertTrue(lines[5].contains("\"phase\":\"run tests\""));
        assertTrue(lines[5].contains("\"depth\":0"));
        assertTrue(lines[5].contains("\"testRunner\":\"junit-console\""));
        assertTrue(lines[5].contains("\"testSourceFiles\":\"1\""));
        assertTrue(lines[5].contains("\"testRuntimeClasspathEntries\""));
        assertTrue(lines[5].contains("\"testLauncherClasspathEntries\""));
        assertTrue(lines[5].contains("\"testDiscoveryScanRoots\""));
        assertTrue(lines[5].contains("\"testPatterns\":\"1\""));
        assertTrue(lines[5].contains("\"testIncludedTags\":\"1\""));
        assertTrue(lines[5].contains("\"testExcludedTags\":\"1\""));
    }

    @Test
    void packageWorkspaceMemberPackagesSelectedMemberOnly() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """
                main = "com.example.api.Api"

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static void main(String[] args) {
                        System.out.println(Core.message());
                    }
                }
                """);

        CommandResult result = execute(
                "package",
                "--workspace",
                "--member", "apps/api",
                "--timings",
                "--timings-format", "json",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Resolved workspace dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("Packaged 1 compiled files as thin jar in apps/api"));
        assertTrue(result.stdout().contains("Included Main-Class manifest entry in apps/api"));
        assertTrue(result.stdout().contains("Packaged 1 workspace members"));
        String[] lines = result.stderr().lines().toArray(String[]::new);
        assertEquals(4, lines.length);
        assertTrue(lines[0].contains("\"phase\":\"plan workspace packages\""));
        assertTrue(lines[0].contains("\"depth\":1"));
        assertTrue(lines[0].contains("\"includedMembers\":\"2\""));
        assertTrue(lines[0].contains("\"selectedMembers\":\"1\""));
        assertTrue(lines[0].contains("\"resolvedLockfile\":\"true\""));
        assertTrue(lines[1].contains("\"phase\":\"build workspace package inputs\""));
        assertTrue(lines[1].contains("\"depth\":1"));
        assertTrue(lines[1].contains("\"members\":\"2\""));
        assertTrue(lines[1].contains("\"sourceFiles\":\"2\""));
        assertTrue(lines[1].contains("\"mainCompilationsSkipped\":\"0\""));
        assertTrue(lines[1].contains("\"mainCompilationsExecuted\":\"2\""));
        assertTrue(lines[2].contains("\"phase\":\"assemble workspace packages\""));
        assertTrue(lines[2].contains("\"depth\":1"));
        assertTrue(lines[2].contains("\"members\":\"1\""));
        assertTrue(lines[2].contains("\"entries\":\"1\""));
        assertTrue(lines[3].contains("\"phase\":\"package workspace\""));
        assertTrue(lines[3].contains("\"depth\":0"));
        assertTrue(lines[3].contains("\"members\":\"1\""));
        assertTrue(lines[3].contains("\"entries\":\"1\""));
        assertTrue(Files.exists(apiDir.resolve("target/api-0.1.0.jar")));
        assertFalse(Files.exists(coreDir.resolve("target/core-0.1.0.jar")));
    }

    @Test
    void runPackageWorkspaceMemberRunsSelectedPackagedApplication() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """
                main = "com.example.api.Api"

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static void main(String[] args) {
                        System.out.println(Core.message() + ":" + args[0]);
                    }
                }
                """);

        CommandResult result = execute(
                "run-package",
                "--workspace",
                "--member", "apps/api",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "hello");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Resolved workspace dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("core:hello"));
        assertTrue(result.stdout().contains("Ran packaged com.example.api.Api in apps/api from "));
        assertTrue(Files.exists(apiDir.resolve("target/api-0.1.0.jar")));
        assertFalse(Files.exists(coreDir.resolve("target/core-0.1.0.jar")));
    }

    @Test
    void runPackageWorkspacePrintsSplitJsonTimingsWhenRequested() throws IOException {
        WorkspaceApplicationFixture fixture = workspaceApplicationFixture("workspace-run-package-timings");

        CommandResult result = execute(
                "run-package",
                "--workspace",
                "--member", "apps/api",
                "--timings",
                "--timings-format", "json",
                "--cwd", fixture.apiDir().toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "hello");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("core:hello"));
        String[] lines = result.stderr().lines().toArray(String[]::new);
        assertEquals(5, lines.length);
        assertTrue(lines[0].contains("\"phase\":\"plan workspace run packages\""));
        assertTrue(lines[0].contains("\"depth\":1"));
        assertTrue(lines[0].contains("\"includedMembers\":\"2\""));
        assertTrue(lines[0].contains("\"selectedMembers\":\"1\""));
        assertTrue(lines[1].contains("\"phase\":\"build workspace run-package inputs\""));
        assertTrue(lines[1].contains("\"depth\":1"));
        assertTrue(lines[1].contains("\"mainCompilationsExecuted\":\"2\""));
        assertTrue(lines[2].contains("\"phase\":\"assemble workspace run packages\""));
        assertTrue(lines[2].contains("\"depth\":1"));
        assertTrue(lines[2].contains("\"entries\":\"1\""));
        assertTrue(lines[3].contains("\"phase\":\"launch workspace packages\""));
        assertTrue(lines[3].contains("\"depth\":1"));
        assertTrue(lines[3].contains("\"members\":\"1\""));
        assertTrue(lines[3].contains("\"outputBytes\""));
        assertTrue(lines[4].contains("\"phase\":\"run workspace packages\""));
        assertTrue(lines[4].contains("\"depth\":0"));
        assertTrue(lines[4].contains("\"mainCompilationsExecuted\":\"2\""));
    }

    @Test
    void runWorkspaceMemberRunsSelectedApplicationFromClasses() throws IOException {
        Path workspaceDir = tempDir.resolve("workspace");
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """
                main = "com.example.api.Api"

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static void main(String[] args) {
                        System.out.println(Core.message() + ":" + args[0]);
                    }
                }
                """);

        CommandResult result = execute(
                "run",
                "--workspace",
                "--member", "apps/api",
                "--cwd", apiDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "hello");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Resolved workspace dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("core:hello"));
        assertTrue(result.stdout().contains("Ran com.example.api.Api in apps/api"));
        assertTrue(Files.exists(apiDir.resolve("target/classes/com/example/api/Api.class")));
        assertTrue(Files.exists(coreDir.resolve("target/classes/com/example/core/Core.class")));
        assertFalse(Files.exists(apiDir.resolve("target/api-0.1.0.jar")));
    }

    @Test
    void runWorkspacePrintsSplitJsonTimingsWhenRequested() throws IOException {
        WorkspaceApplicationFixture fixture = workspaceApplicationFixture("workspace-run-timings");

        CommandResult result = execute(
                "run",
                "--workspace",
                "--member", "apps/api",
                "--timings",
                "--timings-format", "json",
                "--cwd", fixture.apiDir().toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "hello");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("core:hello"));
        String[] lines = result.stderr().lines().toArray(String[]::new);
        assertEquals(4, lines.length);
        assertTrue(lines[0].contains("\"phase\":\"plan workspace run\""));
        assertTrue(lines[0].contains("\"depth\":1"));
        assertTrue(lines[0].contains("\"includedMembers\":\"2\""));
        assertTrue(lines[0].contains("\"selectedMembers\":\"1\""));
        assertTrue(lines[1].contains("\"phase\":\"build workspace run inputs\""));
        assertTrue(lines[1].contains("\"depth\":1"));
        assertTrue(lines[1].contains("\"mainCompilationsExecuted\":\"2\""));
        assertTrue(lines[2].contains("\"phase\":\"launch workspace members\""));
        assertTrue(lines[2].contains("\"depth\":1"));
        assertTrue(lines[2].contains("\"members\":\"1\""));
        assertTrue(lines[2].contains("\"outputBytes\""));
        assertTrue(lines[3].contains("\"phase\":\"run workspace\""));
        assertTrue(lines[3].contains("\"depth\":0"));
        assertTrue(lines[3].contains("\"mainCompilationsExecuted\":\"2\""));
    }

    @Test
    void buildOfflineUsesExistingLockfileAndCache() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """);

        CommandResult result = execute(
                "build",
                "--offline",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Compiled 1 main source files"));
        assertTrue(Files.exists(projectDir.resolve("target/classes/com/example/Main.class")));
    }

    @Test
    void buildReturnsNonZeroOnCompilationFailure() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    missing
                }
                """);

        CommandResult result = execute(
                "build",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: javac failed with exit code"));
        assertTrue(result.stderr().contains("Main.java"));
    }

    @Test
    void runBuildsAndRunsConfiguredMainClass() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("hello " + args[0] + " " + args[1]);
                    }
                }
                """);

        CommandResult result = execute(
                "run",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--",
                "one",
                "two");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("hello one two"));
        assertTrue(result.stdout().contains("Ran com.example.Main"));
        assertTrue(Files.exists(projectDir.resolve("target/classes/com/example/Main.class")));
    }

    @Test
    void runCommandPrintsJsonTimingsWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """);

        CommandResult result = execute(
                "run",
                "--timings",
                "--timings-format", "json",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("hello"));
        assertTrue(result.stdout().contains("Ran com.example.Main"));
        String[] lines = result.stderr().lines().toArray(String[]::new);
        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("\"phase\":\"config read\""));
        assertTrue(lines[0].contains("\"depth\":0"));
        assertTrue(lines[1].contains("\"phase\":\"run application\""));
        assertTrue(lines[1].contains("\"depth\":0"));
        assertTrue(lines[1].contains("\"mainClass\":\"com.example.Main\""));
        assertTrue(lines[1].contains("\"mainSourceFiles\":\"1\""));
        assertTrue(lines[1].contains("\"mainCompilationSkipped\":\"false\""));
        assertTrue(lines[1].contains("\"outputBytes\""));
    }

    @Test
    void runReportsMissingMainClassClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfigWithoutMain(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "run",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("No main class is configured"));
    }

    @Test
    void packagePlanPrintsSpringBootWarDependencyDispositions() throws IOException {
        Path projectDir = tempDir.resolve("package-plan-boot-war");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("package-plan-boot-war") + """

                [package]
                mode = "spring-boot-war"
                """);
        writePackagePlanLockfile(projectDir, false, true);

        CommandResult result = execute(
                "package",
                "--plan",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Package plan"));
        assertTrue(result.stdout().contains("Mode: spring-boot-war"));
        assertTrue(result.stdout().contains("Application layout: WEB-INF/classes"));
        assertTrue(result.stdout().contains("org.springframework.boot:spring-boot-loader:4.0.6 [runtime] loader -> archive root rule=spring-boot-war-loader-expanded"));
        assertTrue(result.stdout().contains("com.example:runtime-lib:1.0.0 [runtime] included -> WEB-INF/lib/runtime-lib-1.0.0.jar rule=spring-boot-war-runtime-lib"));
        assertTrue(result.stdout().contains("jakarta.servlet:jakarta.servlet-api:6.1.0 [provided] provided -> WEB-INF/lib-provided/jakarta.servlet-api-6.1.0.jar rule=spring-boot-war-provided-lib"));
        assertTrue(result.stdout().contains("com.example:devtools:1.0.0 [dev] omitted rule=dev-only-omitted"));
        assertTrue(result.stdout().contains("jakarta.servlet:jakarta.servlet-api:6.1.0 [provided] provided -> WEB-INF/lib-provided/jakarta.servlet-api-6.1.0.jar rule=spring-boot-war-provided-lib lanes=compile packageDefault=false lane=provided-container"));
        assertTrue(result.stdout().contains("com.example:devtools:1.0.0 [dev] omitted rule=dev-only-omitted lanes=runtime,test packageDefault=false lane=development-only"));
        assertTrue(result.stdout().contains("warning CONTAINER_DEPENDENCY_PACKAGED org.apache.tomcat.embed:tomcat-embed-core:10.1.40 rule=spring-boot-war-runtime-lib"));
        assertFalse(Files.exists(projectDir.resolve("target/package-plan-boot-war-0.1.0.war")));
    }

    @Test
    void packagePlanJsonUsesStableShapeForThinJar() throws IOException {
        Path projectDir = tempDir.resolve("package-plan-json");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("package-plan-json"));
        writePackagePlanLockfile(projectDir, true, false);

        CommandResult result = execute(
                "package",
                "--plan",
                "--format", "json",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().startsWith("{\n"));
        assertTrue(result.stdout().contains("\"mode\": \"thin\""));
        assertTrue(result.stdout().contains("\"runtimeClasspath\": \"" + projectDir.resolve("target/package-plan-json-0.1.0.runtime-classpath")));
        assertTrue(result.stdout().contains("\"coordinate\": \"com.example:runtime-lib:1.0.0\""));
        assertTrue(result.stdout().contains("\"lanes\": [\"runtime\", \"test\"]"));
        assertTrue(result.stdout().contains("\"packageDefault\": true"));
        assertTrue(result.stdout().contains("\"laneDisposition\": \"package-default\""));
        assertTrue(result.stdout().contains("\"coordinate\": \"com.example:devtools:1.0.0\""));
        assertTrue(result.stdout().contains("\"packageDefault\": false"));
        assertTrue(result.stdout().contains("\"laneDisposition\": \"development-only\""));
        assertTrue(result.stdout().contains("\"disposition\": \"runtime-classpath\""));
        assertTrue(result.stdout().contains("\"rule\": \"thin-runtime-classpath\""));
        assertTrue(result.stdout().contains("\"policies\": [\"strict-version: com.example:runtime-lib -> 1.0.0 (security baseline)\"]"));
        assertTrue(result.stdout().contains("\"warnings\": []"));
    }

    @Test
    void packageCommandBuildsSpringBootWarWithProvidedTomcatLane() throws IOException {
        Path projectDir = tempDir.resolve("spring-boot-war-provided-tomcat");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        createJarWithEntry(
                cacheRoot.resolve("org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar"),
                "org/springframework/boot/SpringApplication.class");
        createJarWithEntry(
                cacheRoot.resolve(
                        "org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar"),
                "org/springframework/boot/loader/launch/WarLauncher.class");
        createJarWithEntry(
                cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"),
                "com/example/runtime/RuntimeLib.class");
        createJarWithEntry(
                cacheRoot.resolve(
                        "org/apache/tomcat/embed/tomcat-embed-core/10.1.40/tomcat-embed-core-10.1.40.jar"),
                "org/apache/catalina/startup/Tomcat.class");
        createJarWithEntry(
                cacheRoot.resolve("com/example/devtools/1.0.0/devtools-1.0.0.jar"),
                "com/example/dev/DevTools.class");
        createJarWithEntry(
                cacheRoot.resolve("com/example/processor/1.0.0/processor-1.0.0.jar"),
                "com/example/processor/Processor.class");
        writeSpringBootWarProvidedTomcatLockfile(projectDir);

        CommandResult result = execute(
                "package",
                "--mode", "spring-boot-war",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        Path warPath = projectDir.resolve("target/demo-0.1.0.war");
        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Packaged"));
        assertTrue(result.stdout().contains("spring-boot-war"));
        assertTrue(result.stdout().contains("Wrote archive to " + warPath));
        assertTrue(result.stdout().contains("Wrote package evidence to " + warPath + ".zolt-package.json"));
        assertTrue(Files.exists(projectDir.resolve("target/demo-0.1.0.war.zolt-package.json")));
        assertFalse(result.stdout().contains("CONTAINER_DEPENDENCY_PACKAGED"));
        try (JarFile jar = new JarFile(warPath.toFile())) {
            Attributes attributes = jar.getManifest().getMainAttributes();
            assertEquals(
                    "org.springframework.boot.loader.launch.WarLauncher",
                    attributes.getValue(Attributes.Name.MAIN_CLASS));
            assertEquals("com.example.Main", attributes.getValue("Start-Class"));
            assertEquals("WEB-INF/lib-provided/", attributes.getValue("Spring-Boot-Lib-Provided"));
            assertNotNull(jar.getEntry("WEB-INF/lib/runtime-lib-1.0.0.jar"));
            assertNotNull(jar.getEntry("WEB-INF/lib-provided/tomcat-embed-core-10.1.40.jar"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(
                    "WEB-INF/lib/tomcat-embed-core-10.1.40.jar")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(
                    "WEB-INF/lib/devtools-1.0.0.jar")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().equals(
                    "WEB-INF/lib/processor-1.0.0.jar")));
        }
    }

    @Test
    void packageBuildsAndWritesJarWithManifest() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """);

        CommandResult result = execute(
                "package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Resolved dependencies because zolt.lock was missing"));
        assertTrue(result.stdout().contains("Packaged 1 compiled files as thin jar"));
        assertTrue(result.stdout().contains("Included Main-Class manifest entry"));
        assertTrue(result.stdout().contains("Run with: java -jar " + jarPath));
        assertTrue(result.stdout().contains("Run with dependencies: zolt run-package -- [args]"));
        assertTrue(result.stdout().contains("Thin jar: dependencies are not bundled."));
        assertTrue(result.stdout().contains(
                "Wrote runtime classpath to " + projectDir.resolve("target/demo-0.1.0.runtime-classpath")));
        assertTrue(result.stdout().contains("Wrote archive to " + jarPath));
        assertTrue(result.stdout().contains("Wrote package evidence to " + jarPath + ".zolt-package.json"));
        assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
        assertTrue(Files.exists(projectDir.resolve("target/demo-0.1.0.runtime-classpath")));
        assertTrue(Files.exists(projectDir.resolve("target/demo-0.1.0.jar.zolt-package.json")));
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            assertNotNull(jar.getEntry("com/example/Main.class"));
            assertEquals(
                    "com.example.Main",
                    jar.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
        }
    }

    @Test
    void packageCommandPrintsNestedJsonTimingsWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """);

        CommandResult result = execute(
                "package",
                "--timings",
                "--timings-format", "json",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Packaged 1 compiled files as thin jar"));
        String[] lines = result.stderr().lines().toArray(String[]::new);
        assertEquals(4, lines.length);
        assertTrue(lines[0].contains("\"phase\":\"config read\""));
        assertTrue(lines[0].contains("\"depth\":0"));
        assertTrue(lines[1].contains("\"phase\":\"build package inputs\""));
        assertTrue(lines[1].contains("\"depth\":1"));
        assertTrue(lines[1].contains("\"sourceFiles\":\"1\""));
        assertTrue(lines[1].contains("\"mainCompilationSkipped\":\"false\""));
        assertTrue(lines[2].contains("\"phase\":\"assemble package\""));
        assertTrue(lines[2].contains("\"depth\":1"));
        assertTrue(lines[2].contains("\"mode\":\"thin\""));
        assertTrue(lines[2].contains("\"entries\":\"1\""));
        assertTrue(lines[3].contains("\"phase\":\"package\""));
        assertTrue(lines[3].contains("\"depth\":0"));
        assertTrue(lines[3].contains("\"mode\":\"thin\""));
    }

    @Test
    void packageModeOverrideUsesThinForCurrentCommandOnly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.toml"), Files.readString(projectDir.resolve("zolt.toml")) + """

                [package]
                mode = "spring-boot"
                """);
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """);

        CommandResult result = execute(
                "package",
                "--mode", "thin",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Packaged 1 compiled files as thin jar"));
        assertTrue(Files.readString(projectDir.resolve("zolt.toml")).contains("mode = \"spring-boot\""));
        assertTrue(Files.exists(projectDir.resolve("target/demo-0.1.0.jar")));
    }

    @Test
    void packageBuildsSpringBootJarWhenLoaderIsResolved() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.toml"), Files.readString(projectDir.resolve("zolt.toml")) + """

                [package]
                mode = "spring-boot"
                """);
        writeSpringBootLockfile(projectDir, cacheRoot);
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """);

        CommandResult result = execute(
                "package",
                "--cwd", projectDir.toString(),
                "--cache-root", cacheRoot.toString());

        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Packaged 1 compiled files as spring-boot jar"));
        assertTrue(result.stdout().contains("Run with: java -jar " + jarPath));
        assertTrue(result.stdout().contains("Run with Zolt: zolt run-package --mode spring-boot -- [args]"));
        assertTrue(result.stdout().contains("Spring Boot jar: dependencies are nested under BOOT-INF/lib."));
        assertFalse(result.stdout().contains("Thin jar: dependencies are not bundled."));
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            assertNotNull(jar.getEntry("BOOT-INF/classes/com/example/Main.class"));
            assertNotNull(jar.getEntry("BOOT-INF/lib/runtime-lib-1.0.0.jar"));
            assertNotNull(jar.getEntry("org/springframework/boot/loader/launch/JarLauncher.class"));
            assertEquals(
                    "com.example.Main",
                    jar.getManifest().getMainAttributes().getValue("Start-Class"));
        }
    }

    @Test
    void packageReportsMissingSpringBootLoaderClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("zolt.toml"), Files.readString(projectDir.resolve("zolt.toml")) + """

                [package]
                mode = "spring-boot"
                """);
        writeMainSource(projectDir, "package com.example; public final class Main {}\n");
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("requires `org.springframework.boot:spring-boot-loader`"));
        assertTrue(result.stderr().contains("run `zolt resolve`"));
        assertFalse(Files.exists(projectDir.resolve("target/demo-0.1.0.jar")));
    }

    @Test
    void packageRejectsUnknownModeOverride() {
        CommandResult result = execute(
                "package",
                "--mode", "ear",
                "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Unsupported package mode `ear`"));
        assertTrue(result.stderr().contains("thin, spring-boot, war, spring-boot-war, quarkus, uber"));
    }

    @Test
    void packageReturnsNonZeroOnPackagingFailure() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                main = "com.example.Main"

                [repositories]
                central = "https://repo.maven.apache.org/maven2"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "classes"
                testOutput = "test-classes"
                """.formatted(currentJavaMajorVersion()));
        writeMainSource(projectDir, "package com.example; public final class Main {}\n");
        Files.writeString(projectDir.resolve("target"), "not a directory");

        CommandResult result = execute(
                "package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Could not package jar"));
        assertTrue(result.stderr().contains("Check that target/ is writable"));
    }

    @Test
    void runPackageBuildsThinJarAndRunsConfiguredMainClass() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("packaged " + args[0] + " " + args[1]);
                    }
                }
                """);

        CommandResult result = execute(
                "run-package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString(),
                "--",
                "one",
                "two");

        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("packaged one two"));
        assertTrue(result.stdout().contains("Ran packaged com.example.Main from " + jarPath));
        assertTrue(Files.exists(jarPath));
    }

    @Test
    void runPackageCommandPrintsJsonTimingsWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                        System.out.println("packaged");
                    }
                }
                """);

        CommandResult result = execute(
                "run-package",
                "--timings",
                "--timings-format", "json",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("packaged"));
        assertTrue(result.stdout().contains("Ran packaged com.example.Main"));
        String[] lines = result.stderr().lines().toArray(String[]::new);
        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("\"phase\":\"config read\""));
        assertTrue(lines[0].contains("\"depth\":0"));
        assertTrue(lines[1].contains("\"phase\":\"run packaged application\""));
        assertTrue(lines[1].contains("\"depth\":0"));
        assertTrue(lines[1].contains("\"mode\":\"thin\""));
        assertTrue(lines[1].contains("\"entries\":\"1\""));
        assertTrue(lines[1].contains("\"hasMainClass\":\"true\""));
        assertTrue(lines[1].contains("\"mainClass\":\"com.example.Main\""));
        assertTrue(lines[1].contains("\"outputBytes\""));
    }

    @Test
    void runPackageReportsMissingMainClassClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfigWithoutMain(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "run-package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("No main class is configured"));
        assertTrue(result.stderr().contains("[project].main"));
    }

    @Test
    void nativeReportsMissingMainClassClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfigWithoutMain(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "native",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Native Image main class is missing"));
        assertTrue(result.stderr().contains("[project].main"));
    }

    @Test
    void releaseArchiveAssemblesArchiveFromNativeBinary() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(projectDir.resolve("README.md"), "# Demo\n");
        Path binary = projectDir.resolve("target/native/demo");
        Files.createDirectories(binary.getParent());
        Files.writeString(binary, "native");

        CommandResult result = execute(
                "release-archive",
                "--cwd", projectDir.toString(),
                "--target", "linux-x64");

        Path archive = projectDir.resolve("dist/demo-0.1.0-linux-x64.tar.gz");
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Assembled linux-x64 release archive"));
        assertTrue(result.stdout().contains("Included 2 files under demo-0.1.0-linux-x64"));
        assertTrue(result.stdout().contains("Wrote archive to " + archive));
        assertTrue(result.stdout().contains("Wrote checksum to " + archive + ".sha256"));
        assertTrue(result.stdout().contains("Wrote manifest to " + projectDir.resolve("dist/release-manifest.json")));
        assertTrue(Files.exists(archive));
        assertTrue(Files.exists(projectDir.resolve("dist/demo-0.1.0-linux-x64.tar.gz.sha256")));
        assertTrue(Files.exists(projectDir.resolve("dist/release-manifest.json")));
    }

    @Test
    void releaseArchiveReportsMissingBinaryClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "release-archive",
                "--cwd", projectDir.toString(),
                "--target", "linux-x64");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Release archive requires native binary"));
        assertTrue(result.stderr().contains("Run `zolt native` or pass --binary <path>"));
    }

    @Test
    void releaseVerifyReportsMissingArchiveClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute(
                "release-verify",
                "--cwd", projectDir.toString(),
                "dist/missing.tar.gz");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Release archive verification failed for"));
        assertTrue(result.stderr().contains("archive does not exist"));
        assertTrue(result.stderr().contains("Pass a valid release archive path"));
    }

    @Test
    void cleanDeletesBuildOutputWithoutDeletingCache() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.createDirectories(projectDir.resolve("target/classes"));
        Files.writeString(projectDir.resolve("target/classes/Main.class"), "compiled");
        Files.createDirectories(projectDir.resolve(".zolt/cache"));
        Files.writeString(projectDir.resolve(".zolt/cache/artifact.jar"), "cached");

        CommandResult result = execute("clean", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Deleted 1 build output paths"));
        assertFalse(Files.exists(projectDir.resolve("target")));
        assertTrue(Files.exists(projectDir.resolve(".zolt/cache/artifact.jar")));
    }

    @Test
    void cleanDeletesQuarkusOutputLayoutWhenEnabled() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        Files.writeString(
                projectDir.resolve("zolt.toml"),
                Files.readString(projectDir.resolve("zolt.toml"))
                        .replace("output = \"target/classes\"", "output = \"out/main\"")
                        .replace("testOutput = \"target/test-classes\"", "testOutput = \"out/test\""));
        enableQuarkus(projectDir);
        Files.createDirectories(projectDir.resolve("out/main"));
        Files.writeString(projectDir.resolve("out/main/Main.class"), "compiled");
        Files.createDirectories(projectDir.resolve("target/quarkus"));
        Files.writeString(projectDir.resolve("target/quarkus/zolt-augmentation.properties"), "metadata");
        Files.createDirectories(projectDir.resolve("target/quarkus-app"));
        Files.writeString(projectDir.resolve("target/quarkus-app/quarkus-run.jar"), "jar");

        CommandResult result = execute("clean", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Deleted 3 build output paths"));
        assertFalse(Files.exists(projectDir.resolve("out/main")));
        assertFalse(Files.exists(projectDir.resolve("target/quarkus")));
        assertFalse(Files.exists(projectDir.resolve("target/quarkus-app")));
    }

    @Test
    void cleanHandlesMissingTargetCleanly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");

        CommandResult result = execute("clean", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertEquals("Nothing to clean\n", result.stdout());
    }

    @Test
    void testReportsMissingJUnitConsoleClearly() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, "package com.example; public final class Main {}\n");
        Path testSource = projectDir.resolve("src/test/java/com/example/MainTest.java");
        Files.createDirectories(testSource.getParent());
        Files.writeString(testSource, "package com.example; public final class MainTest {}\n");

        CommandResult result = execute(
                "test",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("JUnit Platform Console is not present"));
    }

    @Test
    void doctorReportsJdkStatus() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2", currentJavaMajorVersion());

        CommandResult result = execute("doctor", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("JDK status: ok"));
        assertTrue(result.stdout().contains("java: "));
        assertTrue(result.stdout().contains("javac: "));
        assertTrue(result.stdout().contains("jar: "));
        assertTrue(result.stdout().contains("version: " + currentJavaMajorVersion()));
    }

    @Test
    void doctorReportsSelfHostingReadiness() throws IOException {
        Path projectDir = tempDir.resolve("self-hosting-ready");
        writeSelfHostingProjectConfig(projectDir, true);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        Files.createDirectories(projectDir.resolve("src/main/java"));
        Files.createDirectories(projectDir.resolve("src/test/java"));

        CommandResult result = execute("doctor", "--self-hosting", "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Self-hosting status: ok"));
        assertTrue(result.stdout().contains("ok: main class - project main is com.example.Main"));
        assertTrue(result.stdout().contains("ok: JUnit Platform Console"));
        assertTrue(result.stdout().contains("ok: native no-fallback"));
    }

    @Test
    void doctorReportsSelfHostingGapsWithNextSteps() throws IOException {
        Path projectDir = tempDir.resolve("self-hosting-gaps");
        writeSelfHostingProjectConfig(projectDir, false);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        Files.createDirectories(projectDir.resolve("src/main/java"));
        Files.createDirectories(projectDir.resolve("src/test/java"));

        CommandResult result = execute("doctor", "--self-hosting", "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("Self-hosting status: error"));
        assertTrue(result.stdout().contains("error: JUnit Platform Console - add org.junit.platform:junit-platform-console-standalone to [test.dependencies]"));
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

    private static void writeSelfHostingProjectConfig(Path projectDir, boolean includeTestRunner) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"
                main = "com.example.Main"

                [repositories]
                central = "https://repo.maven.apache.org/maven2"

                %s
                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"

                [native]
                imageName = "demo"
                output = "target/native"
                args = ["--no-fallback"]
                """.formatted(
                currentJavaMajorVersion(),
                includeTestRunner
                        ? """
                        [test.dependencies]
                        "org.junit.platform:junit-platform-console-standalone" = "1.11.4"

                        """
                        : ""));
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }

    private static final class FlushTrackingPrintWriter extends PrintWriter {
        private final StringWriter writer;
        private boolean flushed;

        private FlushTrackingPrintWriter() {
            this(new StringWriter());
        }

        private FlushTrackingPrintWriter(StringWriter writer) {
            super(writer);
            this.writer = writer;
        }

        @Override
        public void flush() {
            flushed = true;
            super.flush();
        }

        private boolean flushed() {
            return flushed;
        }

        private String content() {
            return writer.toString();
        }
    }

    private static void writeProjectConfig(Path projectDir, String repositoryUrl) throws IOException {
        writeProjectConfig(projectDir, repositoryUrl, currentJavaMajorVersion(), Map.of(), Map.of());
    }

    private static void writePolicyProject(Path projectDir) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "%s"

                [repositories]
                test = "https://repo.maven.apache.org/maven2"

                [platforms]
                "org.springframework.boot:spring-boot-dependencies" = "4.0.6"

                [dependencies]
                "com.example:direct-lib" = "1.2.3"
                "org.springframework.boot:spring-boot-starter-web" = {}

                [dependencyPolicy]
                exclude = [
                  { group = "com.example", artifact = "direct-lib", reason = "Direct dependency conflict fixture" },
                  { group = "commons-logging", artifact = "commons-logging", reason = "Use jcl-over-slf4j" },
                  { group = "log4j", artifact = "log4j", reason = "Legacy logging baseline" }
                ]

                [dependencyConstraints]
                "com.example:unused" = { version = "1.0.0", kind = "strict", reason = "Unused baseline" }
                "org.apache.tomcat.embed:tomcat-embed-core" = { version = "10.1.40", kind = "strict", reason = "Container baseline" }

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """.formatted(currentJavaMajorVersion()));
    }

    private static void writePolicyLockfile(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:direct-lib"
                version = "1.2.3"
                source = "maven-central"
                scope = "compile"
                direct = true
                dependencies = []

                [[package]]
                id = "org.springframework.boot:spring-boot-starter-web"
                version = "4.0.6"
                source = "maven-central"
                scope = "compile"
                direct = true
                dependencies = ["org.apache.tomcat.embed:tomcat-embed-core:10.1.40"]
                policies = ["managed-version: org.springframework.boot:spring-boot-starter-web -> 4.0.6 from org.springframework.boot:spring-boot-dependencies:4.0.6"]

                [[package]]
                id = "org.apache.tomcat.embed:tomcat-embed-core"
                version = "10.1.40"
                source = "maven-central"
                scope = "runtime"
                direct = false
                dependencies = []
                policies = ["strict-version: org.apache.tomcat.embed:tomcat-embed-core requested 10.1.39 -> 10.1.40 (Container baseline)"]

                [[policy]]
                kind = "strict-version"
                id = "org.apache.tomcat.embed:tomcat-embed-core"
                requested = "10.1.39"
                source = "org.springframework.boot:spring-boot-starter-web:4.0.6"
                policy = "strict-version: org.apache.tomcat.embed:tomcat-embed-core requested 10.1.39 -> 10.1.40 (Container baseline)"

                [[policy]]
                kind = "global-exclusion"
                id = "commons-logging:commons-logging"
                requested = "1.2"
                source = "org.springframework.boot:spring-boot-starter-web:4.0.6"
                policy = "[dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)"
                """);
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

    private static void enableQuarkus(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("zolt.toml"), Files.readString(projectDir.resolve("zolt.toml")) + """

                [framework.quarkus]
                enabled = true
                package = "fast-jar"
                """);
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

    private static String libraryPackageConfig(String displayName, String moduleName, boolean tests) {
        String testsSetting = tests ? "tests = true\n" : "";
        return """

                [package]
                mode = "thin"
                sources = true
                javadoc = true
                %s
                [package.metadata]
                name = "%s"
                description = "Fixture library metadata for zolt check."
                url = "https://example.com/%s"
                license = "Apache-2.0"
                developers = ["Zolt Team"]
                scm = "https://example.com/%s.git"
                issues = "https://example.com/%s/issues"

                [package.manifest]
                "Automatic-Module-Name" = "%s"
                """.formatted(
                testsSetting,
                displayName,
                moduleName,
                moduleName,
                moduleName,
                moduleName);
    }

    private static String generatedSourceConfig(
            String scope,
            String id,
            String output,
            String input,
            boolean required) {
        return """

                [generated.%s.%s]
                kind = "declared-root"
                language = "java"
                output = "%s"
                inputs = ["%s"]
                required = %s
                """.formatted(scope, id, output, input, required);
    }

    private static void writePackagePlanLockfile(
            Path projectDir,
            boolean includePolicy,
            boolean includeSuspiciousContainerRuntime) throws IOException {
        String policy = includePolicy
                ? """
                policies = ["strict-version: com.example:runtime-lib -> 1.0.0 (security baseline)"]
                """
                : "";
        String suspiciousContainer = includeSuspiciousContainerRuntime
                ? """

                [[package]]
                id = "org.apache.tomcat.embed:tomcat-embed-core"
                version = "10.1.40"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "org/apache/tomcat/embed/tomcat-embed-core/10.1.40/tomcat-embed-core-10.1.40.jar"
                dependencies = []
                """
                : "";
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.springframework.boot:spring-boot"
                version = "4.0.6"
                source = "maven-central"
                scope = "compile"
                direct = false
                jar = "org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar"
                dependencies = []

                [[package]]
                id = "org.springframework.boot:spring-boot-loader"
                version = "4.0.6"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar"
                dependencies = []

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                dependencies = []
                %s

                [[package]]
                id = "jakarta.servlet:jakarta.servlet-api"
                version = "6.1.0"
                source = "maven-central"
                scope = "provided"
                direct = true
                jar = "jakarta/servlet/jakarta.servlet-api/6.1.0/jakarta.servlet-api-6.1.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:devtools"
                version = "1.0.0"
                source = "maven-central"
                scope = "dev"
                direct = true
                jar = "com/example/devtools/1.0.0/devtools-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:test-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "com/example/test-lib/1.0.0/test-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:processor"
                version = "1.0.0"
                source = "maven-central"
                scope = "processor"
                direct = true
                jar = "com/example/processor/1.0.0/processor-1.0.0.jar"
                dependencies = []
                %s
                """.formatted(policy, suspiciousContainer));
    }

    private static void writeSpringBootWarProvidedTomcatLockfile(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.springframework.boot:spring-boot"
                version = "4.0.6"
                source = "maven-central"
                scope = "compile"
                direct = false
                jar = "org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar"
                dependencies = []

                [[package]]
                id = "org.springframework.boot:spring-boot-loader"
                version = "4.0.6"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar"
                dependencies = []

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "org.apache.tomcat.embed:tomcat-embed-core"
                version = "10.1.40"
                source = "maven-central"
                scope = "provided"
                direct = true
                jar = "org/apache/tomcat/embed/tomcat-embed-core/10.1.40/tomcat-embed-core-10.1.40.jar"
                dependencies = []

                [[package]]
                id = "com.example:devtools"
                version = "1.0.0"
                source = "maven-central"
                scope = "dev"
                direct = true
                jar = "com/example/devtools/1.0.0/devtools-1.0.0.jar"
                dependencies = []

                [[package]]
                id = "com.example:processor"
                version = "1.0.0"
                source = "maven-central"
                scope = "processor"
                direct = true
                jar = "com/example/processor/1.0.0/processor-1.0.0.jar"
                dependencies = []
                """);
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

    private static void writeFakeConsoleJar(Path jar) throws IOException {
        Path workDir = jar.getParent().resolve("fake-console-work");
        Path source = workDir.resolve("src/org/junit/platform/console/ConsoleLauncher.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package org.junit.platform.console;

                public final class ConsoleLauncher {
                    private ConsoleLauncher() {
                    }

                    public static void main(String[] args) throws Exception {
                        System.out.println("fake console");
                        for (int index = 0; index + 1 < args.length; index++) {
                            if ("--details".equals(args[index]) && "tree".equals(args[index + 1])) {
                                System.out.println("fake console event output");
                            }
                        }
                        for (int index = 0; index + 1 < args.length; index++) {
                            if ("--reports-dir".equals(args[index])) {
                                java.nio.file.Path reports = java.nio.file.Path.of(args[index + 1]);
                                java.nio.file.Files.createDirectories(reports);
                                java.nio.file.Files.writeString(
                                        reports.resolve("TEST-fake-console.xml"),
                                        "<testsuite name=\\"fake-console\\" tests=\\"1\\" failures=\\"0\\"></testsuite>\\n");
                            }
                        }
                    }
                }
                """);
        Path classes = workDir.resolve("classes");
        new com.zolt.build.JavacRunner().compile(
                currentJavac(),
                java.util.List.of(source),
                new com.zolt.resolve.Classpath(java.util.List.of()),
                classes);

        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            JarEntry entry = new JarEntry("org/junit/platform/console/ConsoleLauncher.class");
            output.putNextEntry(entry);
            output.write(Files.readAllBytes(classes.resolve("org/junit/platform/console/ConsoleLauncher.class")));
            output.closeEntry();
        }
    }

    private static void writeSpringBootLockfile(Path projectDir, Path cacheRoot) throws IOException {
        createJarWithEntry(
                cacheRoot.resolve("org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar"),
                "org/springframework/boot/SpringApplication.class");
        createJarWithEntry(
                cacheRoot.resolve("org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar"),
                "org/springframework/boot/loader/launch/JarLauncher.class");
        createJarWithEntry(
                cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"),
                "com/example/runtime/RuntimeLib.class");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.springframework.boot:spring-boot"
                version = "4.0.6"
                source = "maven-central"
                scope = "compile"
                direct = false
                jar = "org/springframework/boot/spring-boot/4.0.6/spring-boot-4.0.6.jar"
                dependencies = []

                [[package]]
                id = "org.springframework.boot:spring-boot-loader"
                version = "4.0.6"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "org/springframework/boot/spring-boot-loader/4.0.6/spring-boot-loader-4.0.6.jar"
                dependencies = []

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = false
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                dependencies = []
                """);
    }

    private static void createJarWithEntry(Path jar, String entryName) throws IOException {
        createJarWithTextEntry(jar, entryName, "\0");
    }

    private static void createJarWithTextEntry(Path jar, String entryName, String text) throws IOException {
        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(entryName));
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    private static Path currentJavac() {
        return Path.of(System.getProperty("java.home")).resolve("bin").resolve(executable("javac"));
    }

    private static String executable(String name) {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? name + ".exe"
                : name;
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

    private WorkspaceApplicationFixture workspaceApplicationFixture(String name) throws IOException {
        Path workspaceDir = tempDir.resolve(name);
        Path apiDir = workspaceDir.resolve("apps/api");
        Path coreDir = workspaceDir.resolve("modules/core");
        Files.createDirectories(apiDir);
        Files.createDirectories(coreDir);
        Files.writeString(workspaceDir.resolve("zolt-workspace.toml"), """
                [workspace]
                name = "workspace"
                members = ["apps/api", "modules/core"]
                """);
        Files.writeString(coreDir.resolve("zolt.toml"), memberConfig("core"));
        Path coreSource = coreDir.resolve("src/main/java/com/example/core/Core.java");
        Files.createDirectories(coreSource.getParent());
        Files.writeString(coreSource, """
                package com.example.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        Files.writeString(apiDir.resolve("zolt.toml"), memberConfig("api") + """
                main = "com.example.api.Api"

                [dependencies]
                "com.example:core" = { workspace = "modules/core" }
                """);
        Path apiSource = apiDir.resolve("src/main/java/com/example/api/Api.java");
        Files.createDirectories(apiSource.getParent());
        Files.writeString(apiSource, """
                package com.example.api;

                import com.example.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static void main(String[] args) {
                        System.out.println(Core.message() + ":" + args[0]);
                    }
                }
                """);
        return new WorkspaceApplicationFixture(workspaceDir, apiDir, coreDir);
    }

    private record WorkspaceApplicationFixture(Path workspaceDir, Path apiDir, Path coreDir) {
    }

    private static String quarkusInputFingerprint(String output) {
        return output.lines()
                .filter(line -> line.startsWith("Input fingerprint: "))
                .findFirst()
                .orElseThrow()
                .substring("Input fingerprint: ".length());
    }

    private static void writeQuarkusPlanLockfile(Path projectDir) throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "io.quarkus:quarkus-rest"
                version = "3.33.0"
                source = "maven-central"
                scope = "compile"
                direct = true
                jar = "io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar"
                dependencies = []

                [[package]]
                id = "io.quarkus:quarkus-rest-deployment"
                version = "3.33.0"
                source = "maven-central"
                scope = "quarkus-deployment"
                direct = false
                jar = "io/quarkus/quarkus-rest-deployment/3.33.0/quarkus-rest-deployment-3.33.0.jar"
                dependencies = []
                """);
    }

    private static void writeQuarkusAugmentationMetadata(Path projectDir, String inputFingerprint) throws IOException {
        Path metadata = projectDir.resolve("target/quarkus/zolt-augmentation.properties");
        Files.createDirectories(metadata.getParent());
        Files.writeString(metadata, """
                version=1
                inputFingerprint=%s
                """.formatted(inputFingerprint));
    }

    private static final class TestRepository implements AutoCloseable {
        private final HttpServer server;
        private final Map<String, byte[]> responses = new HashMap<>();
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

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
