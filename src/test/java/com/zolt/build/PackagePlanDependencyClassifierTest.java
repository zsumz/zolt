package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.framework.FrameworkPackagePlanDependency;
import com.zolt.framework.FrameworkPackagePlanRules;
import com.zolt.lockfile.LockPackage;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PackagePlanDependencyClassifierTest {
    @Test
    void springBootWarPlacesProvidedDependenciesInProvidedLib() {
        PackagePlanDependency dependency = PackagePlanDependencyClassifier.dependency(
                PackageMode.SPRING_BOOT_WAR,
                lockPackage(
                        "jakarta.servlet",
                        "jakarta.servlet-api",
                        "6.1.0",
                        DependencyScope.PROVIDED,
                        true,
                        "jakarta/servlet/jakarta.servlet-api/6.1.0/jakarta.servlet-api-6.1.0.jar"),
                Set.of(),
                Optional.empty(),
                null);

        assertEquals("jakarta.servlet:jakarta.servlet-api:6.1.0", dependency.coordinate());
        assertEquals("provided", dependency.disposition());
        assertEquals("spring-boot-war-provided-lib", dependency.ruleName());
        assertEquals("WEB-INF/lib-provided/jakarta.servlet-api-6.1.0.jar", dependency.location());
        assertEquals("provided-container", dependency.laneDisposition());
    }

    @Test
    void warOmitsRuntimeCoordinateWhenSameCoordinateIsDirectProvidedDependency() {
        PackageId shared = new PackageId("org.apache.tomcat.embed", "tomcat-embed-core");

        PackagePlanDependency dependency = PackagePlanDependencyClassifier.dependency(
                PackageMode.WAR,
                lockPackage(
                        shared.groupId(),
                        shared.artifactId(),
                        "10.1.40",
                        DependencyScope.RUNTIME,
                        false,
                        "org/apache/tomcat/embed/tomcat-embed-core/10.1.40/tomcat-embed-core-10.1.40.jar"),
                Set.of(shared),
                Optional.empty(),
                null);

        assertEquals("org.apache.tomcat.embed:tomcat-embed-core:10.1.40", dependency.coordinate());
        assertEquals("omitted", dependency.disposition());
        assertEquals("war-provided-coordinate-override", dependency.ruleName());
        assertEquals("", dependency.location());
    }

    @Test
    void quarkusUsesFrameworkRulesWhenConfigured() {
        FrameworkPackagePlanRules rules = new FrameworkPackagePlanRules() {
            @Override
            public boolean supports(PackageMode mode) {
                return mode == PackageMode.QUARKUS;
            }

            @Override
            public FrameworkPackagePlanDependency dependency(LockPackage lockPackage, ProjectConfig config) {
                return new FrameworkPackagePlanDependency(
                        "io.quarkus:quarkus-rest:3.33.0",
                        "3.33.0",
                        DependencyScope.RUNTIME,
                        "included",
                        "quarkus-runtime-lib",
                        "target/quarkus-app/lib/quarkus-rest-3.33.0.jar",
                        "Quarkus runtime dependency is copied into the fast-jar lib directory",
                        List.of("strict-version: io.quarkus:quarkus-rest -> 3.33.0"));
            }

            @Override
            public Path archivePath(Path projectRoot, ProjectConfig config) {
                return projectRoot.resolve("target/quarkus-app/quarkus-run.jar");
            }

            @Override
            public String applicationLayout(ProjectConfig config) {
                return "target/quarkus-app/app";
            }
        };

        PackagePlanDependency dependency = PackagePlanDependencyClassifier.dependency(
                PackageMode.QUARKUS,
                lockPackage(
                        "io.quarkus",
                        "quarkus-rest",
                        "3.33.0",
                        DependencyScope.RUNTIME,
                        true,
                        "io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar"),
                Set.of(),
                Optional.of(rules),
                null);

        assertEquals("included", dependency.disposition());
        assertEquals("quarkus-runtime-lib", dependency.ruleName());
        assertEquals("target/quarkus-app/lib/quarkus-rest-3.33.0.jar", dependency.location());
        assertEquals(List.of("strict-version: io.quarkus:quarkus-rest -> 3.33.0"), dependency.policies());
    }

    private static LockPackage lockPackage(
            String group,
            String artifact,
            String version,
            DependencyScope scope,
            boolean direct,
            String jar) {
        return new LockPackage(
                new PackageId(group, artifact),
                version,
                "maven-central",
                scope,
                direct,
                Optional.of(jar),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of());
    }
}
