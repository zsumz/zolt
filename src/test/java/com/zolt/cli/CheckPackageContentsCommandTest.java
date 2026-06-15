package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CheckPackageContentsCommandTest {
    @TempDir
    private Path tempDir;

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
    void checkPackageContentsReportsPackageRuleDiagnostics() throws IOException {
        Path projectDir = tempDir.resolve("check-package-contents-rules");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-package-contents-rules") + """

                [package]
                mode = "spring-boot-war"
                """);
        writePackagePlanLockfile(projectDir, true, false);

        CommandResult result = execute(
                "check",
                "--cwd", projectDir.toString(),
                "--check", "package-contents");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok package-contents check-package-contents-rules Package mode `spring-boot-war` has"));
        assertTrue(result.stdout().contains("ok package-contents rule:spring-boot-war-provided-lib 1 dependency uses package rule `spring-boot-war-provided-lib` with scope `provided`, disposition `provided`, and location `WEB-INF/lib-provided/*`."));
        assertTrue(result.stdout().contains("ok package-contents rule:dev-only-omitted 1 dependency uses package rule `dev-only-omitted` with scope `dev`, disposition `omitted`, and location `none`."));
        assertTrue(result.stdout().contains("ok package-contents rule:processor-omitted 1 dependency uses package rule `processor-omitted` with scope `processor`, disposition `omitted`, and location `none`."));
        assertTrue(result.stdout().contains("ok package-contents rule:test-omitted 1 dependency uses package rule `test-omitted` with scope `test`, disposition `omitted`, and location `none`."));
        assertTrue(result.stdout().contains("ok package-contents rule:spring-boot-war-runtime-lib"));
        assertTrue(result.stdout().contains("1 includes dependency policy effects."));
    }

    @Test
    void checkContextCiRequiresPackageArtifactWhenConfigured() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-require-package-missing");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-context-ci-require-package-missing"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--require-package",
                "--check", "package-contents",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error package-contents target/check-context-ci-require-package-missing-0.1.0.jar CI context requires the configured package artifact, but it is missing."));
        assertTrue(result.stdout().contains("next: Run `zolt package` before `zolt check --context ci --require-package`."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkContextCiAcceptsRequiredPackageArtifactWithFreshEvidence() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-require-package-ok");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        CommandResult packageResult = execute(
                "package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--require-package",
                "--check", "package-contents",
                "--cwd", projectDir.toString());

        assertEquals(0, packageResult.exitCode());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ok package-contents demo Package mode `thin` has 0 dependency dispositions."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkPackageContentsReportsMissingEvidenceForExistingArchive() throws IOException {
        Path projectDir = tempDir.resolve("check-package-contents-missing-evidence");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        CommandResult packageResult = execute(
                "package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());
        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        Files.delete(projectDir.resolve("target/demo-0.1.0.jar.zolt-package.json"));

        CommandResult result = execute(
                "check",
                "--cwd", projectDir.toString(),
                "--check", "package-contents");

        assertEquals(0, packageResult.exitCode());
        assertEquals(1, result.exitCode());
        assertTrue(Files.exists(jarPath));
        assertTrue(result.stdout().contains("error package-contents target/demo-0.1.0.jar Package artifact exists, but package evidence manifest is missing."));
        assertTrue(result.stdout().contains("next: Run `zolt package` to regenerate target/demo-0.1.0.jar.zolt-package.json."));
        assertEquals("", result.stderr());
    }

    @Test
    void checkPackageContentsReportsStalePackageEvidence() throws IOException {
        Path projectDir = tempDir.resolve("check-package-contents-stale-evidence");
        writeProjectConfig(projectDir, "https://repo.maven.apache.org/maven2");
        writeMainSource(projectDir, """
                package com.example;

                public final class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        CommandResult packageResult = execute(
                "package",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());
        Path jarPath = projectDir.resolve("target/demo-0.1.0.jar");
        Files.writeString(jarPath, "tampered\n", StandardOpenOption.APPEND);

        CommandResult result = execute(
                "check",
                "--cwd", projectDir.toString(),
                "--check", "package-contents");

        assertEquals(0, packageResult.exitCode());
        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("error package-contents target/demo-0.1.0.jar.zolt-package.json Package evidence manifest is stale for `target/demo-0.1.0.jar`."));
        assertTrue(result.stdout().contains("next: Run `zolt package` to regenerate the artifact and evidence manifest."));
        assertEquals("", result.stderr());
    }

    private static void writeProjectConfig(Path projectDir, String repositoryUrl) throws IOException {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("demo") + """
                main = "com.example.Main"

                [repositories]
                test = "%s"

                [dependencies]

                [test.dependencies]

                [build]
                source = "src/main/java"
                test = "src/test/java"
                output = "target/classes"
                testOutput = "target/test-classes"
                """.formatted(repositoryUrl));
    }

    private static void writeMainSource(Path projectDir, String content) throws IOException {
        Path source = projectDir.resolve("src/main/java/com/example/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
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
}
