package sh.zolt.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.BuildSettings;
import sh.zolt.project.CompilerSettings;
import sh.zolt.project.FrameworkSettings;
import sh.zolt.project.NativeSettings;
import sh.zolt.project.PackageMode;
import sh.zolt.project.PackageSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.project.QuarkusPackageMode;
import sh.zolt.project.QuarkusSettings;
import sh.zolt.project.SpringBootSettings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SpringBootNativeProjectDetectorTest {
    @Test
    void detectsSpringBootProjectsAcrossPackageAndDependencyLanes() {
        List<DetectorCase> cases = List.of(
                new DetectorCase("spring boot package", new ConfigBuilder()
                        .packageSettings(new PackageSettings(PackageMode.SPRING_BOOT))
                        .build()),
                new DetectorCase("spring boot war package", new ConfigBuilder()
                        .packageSettings(new PackageSettings(PackageMode.SPRING_BOOT_WAR))
                        .build()),
                new DetectorCase("platform", new ConfigBuilder()
                        .platform("org.springframework.boot:spring-boot-dependencies", "3.3.6")
                        .build()),
                new DetectorCase("api", new ConfigBuilder()
                        .api("org.springframework.boot:spring-boot-starter-validation", "3.3.6")
                        .build()),
                new DetectorCase("managed api", new ConfigBuilder()
                        .managedApi("org.springframework.boot:spring-boot-starter-validation")
                        .build()),
                new DetectorCase("implementation", new ConfigBuilder()
                        .dependency("org.springframework.boot:spring-boot-starter-web", "3.3.6")
                        .build()),
                new DetectorCase("managed implementation", new ConfigBuilder()
                        .managedDependency("org.springframework.boot:spring-boot-starter-web")
                        .build()),
                new DetectorCase("runtime", new ConfigBuilder()
                        .runtime("org.springframework.boot:spring-boot-starter-json", "3.3.6")
                        .build()),
                new DetectorCase("managed runtime", new ConfigBuilder()
                        .managedRuntime("org.springframework.boot:spring-boot-starter-json")
                        .build()),
                new DetectorCase("provided", new ConfigBuilder()
                        .provided("org.springframework.boot:spring-boot-starter-tomcat", "3.3.6")
                        .build()),
                new DetectorCase("managed provided", new ConfigBuilder()
                        .managedProvided("org.springframework.boot:spring-boot-starter-tomcat")
                        .build()),
                new DetectorCase("dev", new ConfigBuilder()
                        .dev("org.springframework.boot:spring-boot-devtools", "3.3.6")
                        .build()),
                new DetectorCase("managed dev", new ConfigBuilder()
                        .managedDev("org.springframework.boot:spring-boot-devtools")
                        .build()),
                new DetectorCase("test", new ConfigBuilder()
                        .test("org.springframework.boot:spring-boot-starter-test", "3.3.6")
                        .build()),
                new DetectorCase("managed test", new ConfigBuilder()
                        .managedTest("org.springframework.boot:spring-boot-starter-test")
                        .build()),
                new DetectorCase("annotation processor", new ConfigBuilder()
                        .annotationProcessor("org.springframework.boot:spring-boot-configuration-processor", "3.3.6")
                        .build()),
                new DetectorCase("managed annotation processor", new ConfigBuilder()
                        .managedAnnotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
                        .build()),
                new DetectorCase("test annotation processor", new ConfigBuilder()
                        .testAnnotationProcessor("org.springframework.boot:spring-boot-test-autoconfigure", "3.3.6")
                        .build()),
                new DetectorCase("managed test annotation processor", new ConfigBuilder()
                        .managedTestAnnotationProcessor("org.springframework.boot:spring-boot-test-autoconfigure")
                        .build()));

        for (DetectorCase detectorCase : cases) {
            assertTrue(
                    SpringBootNativeProjectDetector.springBootProject(detectorCase.config()),
                    detectorCase.name());
        }
    }

    @Test
    void springBootVersionPrefersPlatformAndSkipsBlankDependencyVersions() {
        ProjectConfig platformConfig = new ConfigBuilder()
                .platform("org.springframework.boot:spring-boot-dependencies", "3.3.6")
                .dependency("org.springframework.boot:spring-boot-starter-web", "3.2.12")
                .build();
        ProjectConfig dependencyConfig = new ConfigBuilder()
                .dependency("org.springframework.boot:spring-boot-starter-web", " ")
                .runtime("org.springframework.boot:spring-boot-starter-json", "3.3.7")
                .build();

        assertEquals(Optional.of("3.3.6"), SpringBootNativeProjectDetector.springBootVersion(platformConfig));
        assertEquals(Optional.of("3.3.7"), SpringBootNativeProjectDetector.springBootVersion(dependencyConfig));
        assertEquals(Optional.empty(), SpringBootNativeProjectDetector.springBootVersion(new ConfigBuilder().build()));
    }

    @Test
    void detectsMicronautAndQuarkusBoundariesWithoutSpringBootFalsePositives() {
        ProjectConfig micronaut = new ConfigBuilder()
                .provided("IO.MICRONAUT:micronaut-http-server-netty", "4.10.12")
                .build();
        ProjectConfig quarkusPackage = new ConfigBuilder()
                .packageSettings(new PackageSettings(PackageMode.QUARKUS))
                .build();
        ProjectConfig quarkusFramework = new ConfigBuilder()
                .frameworkSettings(new FrameworkSettings(
                        SpringBootSettings.defaults(),
                        new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)))
                .build();
        ProjectConfig plain = new ConfigBuilder().dependency("org.example:not-spring-boot", "1.0.0").build();

        assertTrue(SpringBootNativeProjectDetector.micronautProject(micronaut));
        assertTrue(SpringBootNativeProjectDetector.quarkusProject(quarkusPackage));
        assertTrue(SpringBootNativeProjectDetector.quarkusProject(quarkusFramework));
        assertFalse(SpringBootNativeProjectDetector.springBootProject(plain));
        assertFalse(SpringBootNativeProjectDetector.micronautProject(plain));
        assertFalse(SpringBootNativeProjectDetector.quarkusProject(plain));
    }

    private record DetectorCase(String name, ProjectConfig config) {
    }

    private static final class ConfigBuilder {
        private Map<String, String> platforms = Map.of();
        private Map<String, String> apiDependencies = Map.of();
        private Set<String> managedApiDependencies = Set.of();
        private Map<String, String> dependencies = Map.of();
        private Set<String> managedDependencies = Set.of();
        private Map<String, String> runtimeDependencies = Map.of();
        private Set<String> managedRuntimeDependencies = Set.of();
        private Map<String, String> providedDependencies = Map.of();
        private Set<String> managedProvidedDependencies = Set.of();
        private Map<String, String> devDependencies = Map.of();
        private Set<String> managedDevDependencies = Set.of();
        private Map<String, String> testDependencies = Map.of();
        private Set<String> managedTestDependencies = Set.of();
        private Map<String, String> annotationProcessors = Map.of();
        private Set<String> managedAnnotationProcessors = Set.of();
        private Map<String, String> testAnnotationProcessors = Map.of();
        private Set<String> managedTestAnnotationProcessors = Set.of();
        private PackageSettings packageSettings = PackageSettings.defaults();
        private FrameworkSettings frameworkSettings = FrameworkSettings.defaults();

        ConfigBuilder platform(String coordinate, String version) {
            platforms = Map.of(coordinate, version);
            return this;
        }

        ConfigBuilder api(String coordinate, String version) {
            apiDependencies = Map.of(coordinate, version);
            return this;
        }

        ConfigBuilder managedApi(String coordinate) {
            managedApiDependencies = Set.of(coordinate);
            return this;
        }

        ConfigBuilder dependency(String coordinate, String version) {
            dependencies = Map.of(coordinate, version);
            return this;
        }

        ConfigBuilder managedDependency(String coordinate) {
            managedDependencies = Set.of(coordinate);
            return this;
        }

        ConfigBuilder runtime(String coordinate, String version) {
            runtimeDependencies = Map.of(coordinate, version);
            return this;
        }

        ConfigBuilder managedRuntime(String coordinate) {
            managedRuntimeDependencies = Set.of(coordinate);
            return this;
        }

        ConfigBuilder provided(String coordinate, String version) {
            providedDependencies = Map.of(coordinate, version);
            return this;
        }

        ConfigBuilder managedProvided(String coordinate) {
            managedProvidedDependencies = Set.of(coordinate);
            return this;
        }

        ConfigBuilder dev(String coordinate, String version) {
            devDependencies = Map.of(coordinate, version);
            return this;
        }

        ConfigBuilder managedDev(String coordinate) {
            managedDevDependencies = Set.of(coordinate);
            return this;
        }

        ConfigBuilder test(String coordinate, String version) {
            testDependencies = Map.of(coordinate, version);
            return this;
        }

        ConfigBuilder managedTest(String coordinate) {
            managedTestDependencies = Set.of(coordinate);
            return this;
        }

        ConfigBuilder annotationProcessor(String coordinate, String version) {
            annotationProcessors = Map.of(coordinate, version);
            return this;
        }

        ConfigBuilder managedAnnotationProcessor(String coordinate) {
            managedAnnotationProcessors = Set.of(coordinate);
            return this;
        }

        ConfigBuilder testAnnotationProcessor(String coordinate, String version) {
            testAnnotationProcessors = Map.of(coordinate, version);
            return this;
        }

        ConfigBuilder managedTestAnnotationProcessor(String coordinate) {
            managedTestAnnotationProcessors = Set.of(coordinate);
            return this;
        }

        ConfigBuilder packageSettings(PackageSettings settings) {
            packageSettings = settings;
            return this;
        }

        ConfigBuilder frameworkSettings(FrameworkSettings settings) {
            frameworkSettings = settings;
            return this;
        }

        ProjectConfig build() {
            return ProjectConfigs.withAllDependencySections(
                            new ProjectMetadata("demo", "1.0.0", "com.example", "21", Optional.empty()),
                            Map.of(),
                            platforms,
                            apiDependencies,
                            managedApiDependencies,
                            Map.of(),
                            dependencies,
                            managedDependencies,
                            Map.of(),
                            runtimeDependencies,
                            managedRuntimeDependencies,
                            providedDependencies,
                            managedProvidedDependencies,
                            devDependencies,
                            managedDevDependencies,
                            testDependencies,
                            managedTestDependencies,
                            Map.of(),
                            annotationProcessors,
                            managedAnnotationProcessors,
                            testAnnotationProcessors,
                            managedTestAnnotationProcessors,
                            BuildSettings.defaults(),
                            NativeSettings.defaults(),
                            CompilerSettings.defaults(),
                            packageSettings)
                    .withFrameworkSettings(frameworkSettings);
        }
    }
}
