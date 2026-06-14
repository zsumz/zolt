package com.zolt.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.JavacRunner;
import com.zolt.test.TestSelection;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkStatus;
import com.zolt.classpath.Classpath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceTestServiceTest {
    private final WorkspaceTestService service = new WorkspaceTestService();

    @TempDir
    private Path tempDir;

    @Test
    void testsWorkspaceMembersInDependencyOrder() throws IOException {
        Path cacheRoot = tempDir.resolve("cache");
        createFakeConsoleJar(cacheRoot.resolve(
                "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"));
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]
                """);
        member("modules/core", "core", "");
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);
        source("apps/api/src/test/java/com/acme/api/ApiTest.java", """
                package com.acme.api;

                import com.acme.core.Core;

                public final class ApiTest {
                    public String message() {
                        return Api.message() + Core.message();
                    }
                }
                """);
        Files.writeString(tempDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.acme:core"
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

        WorkspaceTestResult result = service.test(tempDir.resolve("apps/api"), cacheRoot);

        assertFalse(result.resolvedLockfile());
        assertEquals(List.of("modules/core", "apps/api"), result.builtMembers().stream()
                .map(WorkspaceBuildResult.MemberBuildResult::member)
                .toList());
        assertEquals(List.of("modules/core", "apps/api"), result.members().stream()
                .map(WorkspaceTestResult.MemberTestRunResult::member)
                .toList());
        assertEquals(2, result.mainSourceCount());
        assertEquals(1, result.testSourceCount());
        assertTrue(result.members().stream()
                .map(WorkspaceTestResult.MemberTestRunResult::result)
                .allMatch(member -> member.output().contains("fake console")));
        assertTrue(Files.exists(tempDir.resolve("modules/core/target/classes/com/acme/core/Core.class")));
        assertTrue(Files.exists(tempDir.resolve("apps/api/target/classes/com/acme/api/Api.class")));
        assertTrue(Files.exists(tempDir.resolve("apps/api/target/test-classes/com/acme/api/ApiTest.class")));
        assertFalse(Files.exists(tempDir.resolve("apps/api/zolt.lock")));
        assertFalse(Files.exists(tempDir.resolve("modules/core/zolt.lock")));
    }

    @Test
    void testsSelectedWorkspaceMembersAfterBuildingDependencies() throws IOException {
        Path cacheRoot = tempDir.resolve("cache");
        createFakeConsoleJar(cacheRoot.resolve(
                "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"));
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core", "apps/worker"]
                """);
        member("modules/core", "core", "");
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                public final class Core {
                    private Core() {
                    }

                    public static String message() {
                        return "core";
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;

                public final class Api {
                    private Api() {
                    }

                    public static String message() {
                        return Core.message();
                    }
                }
                """);
        source("apps/api/src/test/java/com/acme/api/ApiTest.java", """
                package com.acme.api;

                public final class ApiTest {
                    public String message() {
                        return Api.message();
                    }
                }
                """);
        member("apps/worker", "worker", "");
        source("apps/worker/src/main/java/com/acme/worker/Worker.java", """
                package com.acme.worker;

                public final class Worker {
                }
                """);
        Files.writeString(tempDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.acme:core"
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

        WorkspaceTestResult result = service.test(
                tempDir,
                cacheRoot,
                new WorkspaceSelectionRequest(false, List.of("apps/api")));

        assertEquals(List.of("modules/core", "apps/api"), result.builtMembers().stream()
                .map(WorkspaceBuildResult.MemberBuildResult::member)
                .toList());
        assertEquals(List.of("apps/api"), result.members().stream()
                .map(WorkspaceTestResult.MemberTestRunResult::member)
                .toList());
        assertTrue(Files.exists(tempDir.resolve("modules/core/target/classes/com/acme/core/Core.class")));
        assertTrue(Files.exists(tempDir.resolve("apps/api/target/classes/com/acme/api/Api.class")));
        assertTrue(Files.exists(tempDir.resolve("apps/api/target/test-classes/com/acme/api/ApiTest.class")));
        assertFalse(Files.exists(tempDir.resolve("apps/worker/target/classes/com/acme/worker/Worker.class")));
    }

    @Test
    void appliesTestSelectionToSelectedWorkspaceMembersAfterBuildingDependencies() throws IOException {
        Path cacheRoot = tempDir.resolve("cache");
        createFakeConsoleJar(cacheRoot.resolve(
                "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"));
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]
                """);
        member("modules/core", "core", "");
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                public final class Core {
                    public static String message() {
                        return "core";
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;

                public final class Api {
                    public static String message() {
                        return Core.message();
                    }
                }
                """);
        source("apps/api/src/test/java/com/acme/api/ApiTest.java", """
                package com.acme.api;

                public final class ApiTest {
                    public String message() {
                        return Api.message();
                    }
                }
                """);
        Files.writeString(tempDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.acme:core"
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
        TestSelection testSelection = TestSelection.fromCli(
                List.of(),
                List.of("*ApiTest"),
                List.of("fast"),
                List.of("slow"));

        WorkspaceTestResult result = service.test(
                tempDir,
                cacheRoot,
                new WorkspaceSelectionRequest(false, List.of("apps/api")),
                testSelection);

        assertEquals(List.of("modules/core", "apps/api"), result.builtMembers().stream()
                .map(WorkspaceBuildResult.MemberBuildResult::member)
                .toList());
        assertEquals(List.of("apps/api"), result.members().stream()
                .map(WorkspaceTestResult.MemberTestRunResult::member)
                .toList());
        assertEquals(testSelection, result.members().getFirst().result().testSelection());
        assertEquals(1, result.testPatternCount());
        assertEquals(1, result.testIncludedTagCount());
        assertEquals(1, result.testExcludedTagCount());
        assertTrue(Files.exists(tempDir.resolve("modules/core/target/classes/com/acme/core/Core.class")));
        assertTrue(Files.exists(tempDir.resolve("apps/api/target/test-classes/com/acme/api/ApiTest.class")));
    }

    @Test
    void sharesCachedJdkDetectionAcrossWorkspaceBuildAndTestExecution() throws IOException {
        Path cacheRoot = tempDir.resolve("cache");
        createFakeConsoleJar(cacheRoot.resolve(
                "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"));
        workspace("""
                [workspace]
                name = "acme-platform"
                members = ["apps/api", "modules/core"]
                """);
        member("modules/core", "core", "");
        source("modules/core/src/main/java/com/acme/core/Core.java", """
                package com.acme.core;

                public final class Core {
                    public static String message() {
                        return "core";
                    }
                }
                """);
        member("apps/api", "api", """

                [dependencies]
                "com.acme:core" = { workspace = "modules/core" }
                """);
        source("apps/api/src/main/java/com/acme/api/Api.java", """
                package com.acme.api;

                import com.acme.core.Core;

                public final class Api {
                    public static String message() {
                        return Core.message();
                    }
                }
                """);
        source("apps/api/src/test/java/com/acme/api/ApiTest.java", """
                package com.acme.api;

                public final class ApiTest {
                    public String message() {
                        return Api.message();
                    }
                }
                """);
        Files.writeString(tempDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.acme:core"
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
        CachingJdkChecker jdkChecker = new CachingJdkChecker();
        WorkspaceTestService service = new WorkspaceTestService(jdkChecker);

        WorkspaceTestResult result = service.test(
                tempDir,
                cacheRoot,
                new WorkspaceSelectionRequest(false, List.of("apps/api")));

        assertEquals(List.of("modules/core", "apps/api"), result.builtMembers().stream()
                .map(WorkspaceBuildResult.MemberBuildResult::member)
                .toList());
        assertEquals(List.of("apps/api"), result.members().stream()
                .map(WorkspaceTestResult.MemberTestRunResult::member)
                .toList());
        assertEquals(4, jdkChecker.detectCalls());
        assertEquals(1, jdkChecker.toolchainReads());
    }

    private void workspace(String content) throws IOException {
        Files.writeString(tempDir.resolve("zolt-workspace.toml"), content);
    }

    private void member(String path, String name, String extraToml) throws IOException {
        Path member = tempDir.resolve(path);
        Files.createDirectories(member);
        Files.writeString(member.resolve("zolt.toml"), """
                [project]
                name = "%s"
                version = "0.1.0"
                group = "com.acme"
                java = "%s"
                %s""".formatted(name, currentJavaMajorVersion(), extraToml));
    }

    private void source(String path, String content) throws IOException {
        Path source = tempDir.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }

    private void createFakeConsoleJar(Path jar) throws IOException {
        Path source = tempDir.resolve("fake-console-src/org/junit/platform/console/ConsoleLauncher.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package org.junit.platform.console;

                public final class ConsoleLauncher {
                    private ConsoleLauncher() {
                    }

                    public static void main(String[] args) {
                        System.out.println("fake console");
                    }
                }
                """);
        Path classes = tempDir.resolve("fake-console-classes");
        new JavacRunner().compile(
                currentJavac(),
                List.of(source),
                new Classpath(List.of()),
                classes);

        Files.createDirectories(jar.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            JarEntry entry = new JarEntry("org/junit/platform/console/ConsoleLauncher.class");
            output.putNextEntry(entry);
            output.write(Files.readAllBytes(classes.resolve("org/junit/platform/console/ConsoleLauncher.class")));
            output.closeEntry();
        }
    }

    private static Path currentJavac() {
        return Path.of(System.getProperty("java.home")).resolve("bin").resolve(executable("javac"));
    }

    private static String executable(String name) {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
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

    private static final class CachingJdkChecker implements JdkChecker {
        private int detectCalls;
        private int toolchainReads;
        private JdkStatus status;

        @Override
        public JdkStatus detect(String requiredVersion) {
            detectCalls++;
            if (status == null) {
                toolchainReads++;
                Path javaHome = Path.of(System.getProperty("java.home"));
                status = new JdkStatus(
                        Optional.of(javaHome),
                        Optional.of(javaHome.resolve("bin").resolve(executable("java"))),
                        Optional.of(javaHome.resolve("bin").resolve(executable("javac"))),
                        Optional.of(javaHome.resolve("bin").resolve(executable("jar"))),
                        Optional.of(requiredVersion),
                        requiredVersion);
            }
            return status;
        }

        int detectCalls() {
            return detectCalls;
        }

        int toolchainReads() {
            return toolchainReads;
        }
    }
}
