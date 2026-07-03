package sh.zolt.classpath;

import sh.zolt.dependency.PackageId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ClasspathModelTest {
    @Test
    void classpathSetSixArgConstructorDefaultsTestCompileToTestRuntime() {
        Classpath test = new Classpath(List.of(Path.of("test.jar")));
        ClasspathSet classpaths = new ClasspathSet(
                new Classpath(List.of()),
                new Classpath(List.of()),
                test,
                new Classpath(List.of()),
                new Classpath(List.of()),
                new Classpath(List.of()));

        assertSame(test, classpaths.testCompile());
    }

    @Test
    void resolvedPackageCarriesClasspathInputsWithoutHttpOrCliConcerns() {
        ResolvedPackage resolvedPackage = new ResolvedPackage(
                new PackageId("com.google.guava", "guava"),
                "33.4.0-jre",
                true,
                Path.of("cache/com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom"),
                Path.of("cache/com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.jar"));

        assertTrue(resolvedPackage.direct());
        assertEquals("33.4.0-jre", resolvedPackage.selectedVersion());
        assertEquals("guava-33.4.0-jre.jar", resolvedPackage.jarPath().getFileName().toString());
    }
}
