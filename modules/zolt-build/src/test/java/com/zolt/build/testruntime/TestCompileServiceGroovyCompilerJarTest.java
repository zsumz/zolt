package com.zolt.build.testruntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.incremental.IncrementalCompileState;
import com.zolt.build.incremental.IncrementalCompileStateCodec;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestCompileServiceGroovyCompilerJarTest extends TestCompileServiceGroovyTestSupport {
    private final TestCompileService testCompileService = new TestCompileService();

    @TempDir
    private Path projectDir;

    @Test
    void compilesGroovyTestSourcesWithProjectProvidedCompilerJar() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path groovyJar = cacheRoot.resolve("org/apache/groovy/groovy/4.0.24/groovy-4.0.24.jar");
        createFakeGroovyCompilerJar(projectDir, groovyJar);
        TestCompileServiceGroovyTest.writeLockfile(projectDir, """
                version = 1

                [[package]]
                id = "org.apache.groovy:groovy"
                version = "4.0.24"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/apache/groovy/groovy/4.0.24/groovy-4.0.24.jar"
                dependencies = []
                """);
        TestCompileServiceGroovyTest.source(projectDir, "src/test/groovy/com/example/MainSpec.groovy", """
                package com.example

                final class MainSpec {
                }
                """);
        ProjectConfig config = config().withBuildSettings(new BuildSettings(
                "src/main/java",
                "src/test/java",
                "target/classes",
                "target/test-classes",
                List.of("src/test/java"),
                List.of("src/test/groovy")));

        TestCompileResult first = testCompileService.compileTests(projectDir, config, cacheRoot);
        TestCompileResult second = testCompileService.compileTests(projectDir, config, cacheRoot);

        assertEquals(1, first.sourceCount());
        assertEquals("full", first.testCompilationMode());
        assertEquals("groovy-test-sources", first.testIncrementalFallbackReason());
        assertTrue(first.compilerOutput().contains("fake groovy compiler"));
        assertTrue(Files.exists(projectDir.resolve("target/test-classes/com/example/MainSpec.class")));
        IncrementalCompileState state = new IncrementalCompileStateCodec()
                .read(projectDir.resolve("target/test-classes/.zolt-incremental-test.state"))
                .orElseThrow();
        assertTrue(state.fallbackReasons().contains("groovy-test-sources"));
        assertTrue(state.fallbackReasons().contains("unreadable-class-output"));
        assertTrue(second.testCompilationSkipped());
    }

    private static ProjectConfig config() {
        return TestCompileServiceGroovyTest.config();
    }
}
