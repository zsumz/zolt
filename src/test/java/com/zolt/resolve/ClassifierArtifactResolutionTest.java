package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.maven.PomInterpolationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ClassifierArtifactResolutionTest extends ResolveServiceTestSupport {
    @Test
    void resolvesFixedRuntimeClassifierArtifactFromPomDependency() {
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>native-lib</artifactId>
                      <version>1.0.0</version>
                      <scope>runtime</scope>
                      <classifier>linux-x86_64</classifier>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "native-lib", "1.0.0", simplePom("com.example", "native-lib", "1.0.0"));
        addClassifierJar("com.example", "native-lib", "1.0.0", "linux-x86_64", Map.of(
                "com/example/NativeMarker.class", "classifier jar"));
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                configWithDependencies(Map.of("com.example:app", "1.0.0")),
                cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage nativeLib = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "native-lib")))
                .findFirst()
                .orElseThrow();
        assertEquals(DependencyScope.RUNTIME, nativeLib.scope());
        assertEquals(
                "com/example/native-lib/1.0.0/native-lib-1.0.0-linux-x86_64.jar",
                nativeLib.jar().orElseThrow());
        assertEquals(1, requestCount("/maven2/com/example/native-lib/1.0.0/native-lib-1.0.0-linux-x86_64.jar"));
        assertEquals(0, requestCount("/maven2/com/example/native-lib/1.0.0/native-lib-1.0.0.jar"));
    }

    @Test
    void offlineResolveReportsMissingClassifierArtifactWithCacheSeedingStep() throws IOException {
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>native-lib</artifactId>
                      <version>1.0.0</version>
                      <scope>runtime</scope>
                      <classifier>linux-x86_64</classifier>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "native-lib", "1.0.0", simplePom("com.example", "native-lib", "1.0.0"));
        addClassifierJar("com.example", "native-lib", "1.0.0", "linux-x86_64", Map.of());
        Path projectDir = tempDir.resolve("offline-project");
        Path cacheRoot = tempDir.resolve("offline-cache");
        createDirectory(projectDir);
        resolveService.resolve(projectDir, configWithDependencies(Map.of("com.example:app", "1.0.0")), cacheRoot);
        Files.delete(cacheRoot.resolve("com/example/native-lib/1.0.0/native-lib-1.0.0-linux-x86_64.jar"));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(
                        projectDir,
                        configWithDependencies(Map.of("com.example:app", "1.0.0")),
                        cacheRoot,
                        false,
                        true));

        assertTrue(exception.getMessage().contains("native-lib-1.0.0-linux-x86_64.jar"));
        assertTrue(exception.getMessage().contains("Run the command without --offline"));
    }

    @Test
    void rejectsDynamicClassifierSelectionWithActionableDiagnostic() {
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>native-lib</artifactId>
                      <version>1.0.0</version>
                      <classifier>${os.detected.classifier}</classifier>
                    </dependency>
                  </dependencies>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        createDirectory(projectDir);

        PomInterpolationException exception = assertThrows(
                PomInterpolationException.class,
                () -> resolveService.resolve(
                        projectDir,
                        configWithDependencies(Map.of("com.example:app", "1.0.0")),
                        tempDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("Dynamic classifier selection"));
        assertTrue(exception.getMessage().contains("${os.detected.classifier}"));
        assertTrue(exception.getMessage().contains("fixed OS/architecture classifier"));
    }
}
