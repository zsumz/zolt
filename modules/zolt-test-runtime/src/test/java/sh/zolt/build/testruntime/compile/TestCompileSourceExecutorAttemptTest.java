package sh.zolt.build.testruntime.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import sh.zolt.build.CompileDiagnostics;
import sh.zolt.build.compile.JavacResult;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class TestCompileSourceExecutorAttemptTest {
    @Test
    void sourceCountAndOutputCombineJavaAndGroovyResults() {
        TestCompileSourceExecutor.Attempt attempt = attempt("javac output", "groovy output");

        assertEquals(5, attempt.sourceCount());
        assertEquals(Path.of("target/test-classes"), attempt.outputDirectory());
        assertEquals("javac output\ngroovy output", attempt.output());
        assertEquals("full", attempt.mode());
        assertEquals("fallback", attempt.fallbackReason());
        assertEquals(new CompileDiagnostics(1, 2, 3, 4, 5, 6, 7, 8), attempt.diagnostics());
    }

    @Test
    void outputUsesGroovyOutputWhenJavacOutputIsBlank() {
        assertEquals("groovy output", attempt("", "groovy output").output());
        assertEquals("groovy output", attempt(null, "groovy output").output());
    }

    @Test
    void outputUsesJavacOutputWhenGroovyOutputIsBlank() {
        assertEquals("javac output", attempt("javac output", "").output());
        assertEquals("javac output", attempt("javac output", null).output());
    }

    @Test
    void outputPreservesExistingTrailingNewlineBetweenCompilerOutputs() {
        assertEquals("javac output\ngroovy output", attempt("javac output\n", "groovy output").output());
    }

    private static TestCompileSourceExecutor.Attempt attempt(String javacOutput, String groovyOutput) {
        return new TestCompileSourceExecutor.Attempt(
                new JavacResult(3, Path.of("target/test-classes"), javacOutput),
                new JavacResult(2, Path.of("target/test-classes"), groovyOutput),
                "full",
                "fallback",
                new CompileDiagnostics(1, 2, 3, 4, 5, 6, 7, 8));
    }
}
