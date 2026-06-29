package com.zolt.quarkus.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.testworker.QuarkusTestRunnerDescriptor;
import com.zolt.quarkus.testworker.QuarkusTestRunnerRequest;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusAnnotationApiProbeTest {
    private static final String SESSION_SERVICE = "META-INF/services/com.example.LauncherSessionListener";
    private static final String DISCOVERY_SERVICE = "META-INF/services/com.example.LauncherDiscoveryListener";
    private static final String ARTIFACT_PROVIDER_SERVICE = "META-INF/services/com.example.ArtifactLauncherProvider";

    @TempDir
    private Path projectDir;

    @Test
    void probesQuarkusAnnotationApiSurface() throws IOException {
        writeService(SESSION_SERVICE, ValidLauncherInterceptor.class.getName());
        writeService(DISCOVERY_SERVICE, ValidLauncherInterceptor.class.getName());
        writeService(ARTIFACT_PROVIDER_SERVICE, ValidArtifactProvider.class.getName());
        QuarkusAnnotationApiProbe probe = new QuarkusAnnotationApiProbe(classLoader(), contract(
                ValidExtension.class.getName(),
                ValidLauncherInterceptor.class.getName()));

        QuarkusAnnotationApi api = probe.probe(descriptor());

        assertEquals(ValidExtension.class.getName(), api.extensionClass());
        assertEquals(ValidProfile.class.getName(), api.testProfileClass());
        assertEquals(ValidLauncherInterceptor.class.getName(), api.launcherInterceptorClass());
        assertEquals(List.of(ValidArtifactProvider.class.getName()), api.artifactLauncherProviders());
    }

    @Test
    void rejectsMissingExtensionClass() throws IOException {
        writeService(SESSION_SERVICE, ValidLauncherInterceptor.class.getName());
        writeService(DISCOVERY_SERVICE, ValidLauncherInterceptor.class.getName());
        writeService(ARTIFACT_PROVIDER_SERVICE, ValidArtifactProvider.class.getName());
        QuarkusAnnotationApiProbe probe = new QuarkusAnnotationApiProbe(classLoader(), contract(
                "missing.QuarkusTestExtension",
                ValidLauncherInterceptor.class.getName()));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> probe.probe(descriptor()));

        assertTrue(exception.getMessage().contains("classpath is missing missing.QuarkusTestExtension"));
        assertTrue(exception.getMessage().contains("Quarkus test dependencies"));
    }

    @Test
    void rejectsMissingLauncherServiceProvider() throws IOException {
        writeService(DISCOVERY_SERVICE, ValidLauncherInterceptor.class.getName());
        writeService(ARTIFACT_PROVIDER_SERVICE, ValidArtifactProvider.class.getName());
        QuarkusAnnotationApiProbe probe = new QuarkusAnnotationApiProbe(classLoader(), contract(
                ValidExtension.class.getName(),
                ValidLauncherInterceptor.class.getName()));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> probe.probe(descriptor()));

        assertTrue(exception.getMessage().contains("missing service provider"));
        assertTrue(exception.getMessage().contains(SESSION_SERVICE));
    }

    @Test
    void rejectsIncompatibleLauncherInterceptor() throws IOException {
        writeService(SESSION_SERVICE, IncompatibleLauncherInterceptor.class.getName());
        writeService(DISCOVERY_SERVICE, IncompatibleLauncherInterceptor.class.getName());
        writeService(ARTIFACT_PROVIDER_SERVICE, ValidArtifactProvider.class.getName());
        QuarkusAnnotationApiProbe probe = new QuarkusAnnotationApiProbe(classLoader(), contract(
                ValidExtension.class.getName(),
                IncompatibleLauncherInterceptor.class.getName()));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> probe.probe(descriptor()));

        assertTrue(exception.getMessage().contains("incompatible launcher session listener"));
        assertTrue(exception.getMessage().contains(IncompatibleLauncherInterceptor.class.getName()));
    }

    private void writeService(String service, String provider) throws IOException {
        Path serviceFile = projectDir.resolve(service);
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, provider + "\n");
    }

    private ClassLoader classLoader() throws IOException {
        URL[] urls = new URL[] {projectDir.toUri().toURL()};
        return new URLClassLoader(urls, getClass().getClassLoader());
    }

    private static QuarkusAnnotationApiProbe.Contract contract(
            String extensionClass,
            String launcherInterceptorClass) {
        return new QuarkusAnnotationApiProbe.Contract(
                extensionClass,
                ValidProfile.class.getName(),
                launcherInterceptorClass,
                LauncherSessionListener.class.getName(),
                LauncherDiscoveryListener.class.getName(),
                TestExecutionListener.class.getName(),
                ArtifactLauncherProvider.class.getName(),
                SESSION_SERVICE,
                DISCOVERY_SERVICE,
                ARTIFACT_PROVIDER_SERVICE);
    }

    private static QuarkusTestRunnerDescriptor descriptor() {
        return new QuarkusTestRunnerDescriptor(
                Path.of("/repo/target/quarkus/zolt-test-bootstrap.properties"),
                Path.of("/repo/target/quarkus/test-runtime-classpath.txt"),
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/test-classes"),
                Path.of("/repo/target/quarkus/test-application-model.dat"),
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                QuarkusTestRunnerRequest.RUNNER_MODE,
                true,
                true,
                List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit-platform-console.jar")));
    }

    public static final class ValidExtension {
        public ValidExtension() {
        }
    }

    public interface ValidProfile {
    }

    public interface LauncherSessionListener {
    }

    public interface LauncherDiscoveryListener {
    }

    public interface TestExecutionListener {
    }

    public interface ArtifactLauncherProvider {
    }

    public static final class ValidLauncherInterceptor
            implements LauncherSessionListener, LauncherDiscoveryListener, TestExecutionListener {
    }

    public static final class IncompatibleLauncherInterceptor {
    }

    public static final class ValidArtifactProvider implements ArtifactLauncherProvider {
    }
}
