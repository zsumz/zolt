package sh.zolt.classpath;

import sh.zolt.dependency.PackageId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ClasspathModelTest {
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
