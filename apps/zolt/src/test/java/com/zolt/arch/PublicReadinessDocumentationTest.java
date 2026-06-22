package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PublicReadinessDocumentationTest {
    @Test
    void readmeDoesNotPublishStaleSpringBootNativeStatus() throws IOException {
        String readme = Files.readString(RepositoryPaths.root().resolve("README.md"));

        assertFalse(
                readme.contains("Spring Boot AOT/native is explicitly unsupported in the public beta"),
                "README must not contradict the explicit Spring Boot AOT/native canary path");
        assertTrue(
                readme.contains("[framework.springBoot.native] enabled = true"),
                "README should name the explicit Spring Boot native support flag");
        assertTrue(
                readme.contains("one real Spring Boot 3.3 WebMVC Java 21 native executable canary passes"),
                "README should mention the proven narrow Spring Boot native executable canary");
        assertTrue(
                readme.contains("broader Spring native coverage is still beta-hardening work"),
                "README should keep the remaining Spring Boot native support limit visible");
        assertTrue(
                readme.contains("not broad real-app native-image support"),
                "README should not over-claim Spring Boot native-image support");
    }

    @Test
    void adoptionReadinessListsPublicSupportSurfaceBoundaries() throws IOException {
        String readiness = Files.readString(RepositoryPaths.root().resolve("docs/adoption-readiness.md"));

        assertTrue(readiness.contains("## Public Support Surface"));
        assertTrue(readiness.contains("| Plain Java applications | Supported |"));
        assertTrue(readiness.contains("| Spring Boot AOT/native | Supported canary path, not broad real-app support |"));
        assertTrue(readiness.contains("| Quarkus JVM applications | Experimental |"));
        assertTrue(readiness.contains("| Groovy and Spock | Planned |"));
        assertTrue(readiness.contains("Non-goal for public beta"));
        assertTrue(readiness.contains("Kotlin, Scala, Android, SNAPSHOTs, and version ranges in the public MVP"));
        assertFalse(
                readiness.contains("Kotlin JVM source support."),
                "Kotlin must stay out of planned public-MVP readiness docs");
        assertFalse(
                readiness.contains("- [ ] Public readiness docs list supported, experimental, planned, and non-goal surfaces."),
                "Release readiness should not show the public support-surface docs item as incomplete");
    }

    @Test
    void adoptionReadinessDoesNotCallImplementedGuardrailsBlockers() throws IOException {
        String readiness = Files.readString(RepositoryPaths.root().resolve("docs/adoption-readiness.md"));

        assertTrue(readiness.contains("Core adoption guardrails:"));
        assertTrue(readiness.contains(""));
        assertTrue(readiness.contains(""));
        assertTrue(readiness.contains(""));
        assertFalse(
                readiness.contains("Top adoption blockers:"),
                "Implemented cache, stale-lock, and smoke guardrails should not be labeled as active blockers");
    }

    @Test
    void testingStrategyKeepsKotlinPostMvp() throws IOException {
        String testingStrategy = Files.readString(RepositoryPaths.root().resolve("docs/testing-strategy.md"));

        assertTrue(testingStrategy.contains("Kotlin remains future post-MVP source-language work"));
        assertTrue(testingStrategy.contains("should not be treated as public-beta test support"));
        assertFalse(
                testingStrategy.contains("Kotlin JVM source support, and multiple test engines continue to grow"),
                "Testing strategy must not imply Kotlin is growing as current public-beta support");
    }

    @Test
    void commandDocsDescribeTypedFrameworkNativeMigration() throws IOException {
        String commands = Files.readString(RepositoryPaths.root().resolve("docs/commands.md"));

        assertTrue(commands.contains("external framework AOT/native/dev-mode tasks"));
        assertTrue(commands.contains("typed Zolt framework settings such as `[framework.springBoot.native] enabled = true`"));
        assertTrue(commands.contains("instead of executing Maven or Gradle native tasks"));
        assertFalse(
                commands.contains("framework-native modes require dedicated Zolt support before they are accepted"),
                "Command docs must not contradict the explicit Spring Boot native path");
    }

    @Test
    void releasePackagingDoesNotPretendLicenseExists() throws IOException {
        String releasePackaging = Files.readString(RepositoryPaths.root().resolve("docs/release-packaging.md"));

        assertTrue(releasePackaging.contains("LICENSE (only after the repository has a license file)"));
        assertTrue(releasePackaging.contains("release archives should not pretend to include a license"));
        assertFalse(
                releasePackaging.contains("└── LICENSE\n```"),
                "Archive layout must not show LICENSE as unconditional before a license decision");
    }

    @Test
    void productVisionDoesNotClaimGradleMavenBootstrapForV01() throws IOException {
        String productVision = Files.readString(RepositoryPaths.root().resolve("docs/product-vision.md"));

        assertTrue(productVision.contains("Gradle-free JVM bootstrap"));
        assertTrue(productVision.contains("Zolt builds, tests, packages, smokes, and parity-checks itself"));
        assertFalse(
                productVision.contains("v0.1: built with Gradle/Maven bootstrap"),
                "Product vision must not describe v0.1 as Gradle/Maven-bootstrapped after Zolt-owned self-hosting landed");
    }

    @Test
    void roadmapKeepsSelfHostingStatusCurrent() throws IOException {
        String roadmap = Files.readString(RepositoryPaths.root().resolve("docs/roadmap.md"));

        assertTrue(roadmap.contains("the current repository has since removed that bootstrap"));
        assertTrue(roadmap.contains("The old Gradle fallback native task was removed with the root Gradle bootstrap"));
        assertTrue(roadmap.contains("native self-hosting now runs through `scripts/self-host-native`"));
        assertTrue(roadmap.contains("Goal: make Native Image support Zolt-owned command behavior instead of Gradle bootstrap behavior."));
        assertFalse(
                roadmap.contains("Goal: make the current Zolt repository buildable by Zolt without removing the Gradle bootstrap."),
                "Roadmap must not present the removed Gradle bootstrap as a current constraint");
        assertFalse(
                roadmap.contains("Goal: move native-image support from Gradle bootstrap tasks into Zolt-owned command behavior."),
                "Roadmap must not describe native self-hosting as still moving out of Gradle bootstrap tasks");
        assertFalse(
                roadmap.contains("The Gradle fallback `nativeImage` task delegates"),
                "Roadmap must not describe a removed Gradle fallback native task as current status");
    }

    @Test
    void frameworkReadinessDoesNotPublishQuarkusAnnotationTests() throws IOException {
        String frameworkReadiness = Files.readString(RepositoryPaths.root().resolve("docs/framework-readiness.md"));

        assertTrue(frameworkReadiness.contains("exercise descriptor-enabled `@QuarkusTest` probes only to explicit Zolt-shaped blocker diagnostics without public enablement"));
        assertTrue(frameworkReadiness.contains("public Quarkus `@QuarkusTest` enablement"));
        assertFalse(
                frameworkReadiness.contains("pass the descriptor-enabled `@QuarkusTest` REST Assured probe"),
                "Framework readiness must not imply public Quarkus annotation-test support is complete");
    }

    @Test
    void roadmapDoesNotTreatSpringBootAotNativeAsWhollyFuture() throws IOException {
        String roadmap = Files.readString(RepositoryPaths.root().resolve("docs/roadmap.md"));

        assertTrue(roadmap.contains("Explicit Spring Boot AOT/native canaries now run Spring Boot AOT through Zolt-owned tooling"));
        assertTrue(roadmap.contains("[framework.springBoot.native] enabled = true"));
        assertTrue(roadmap.contains("Spring Boot 3.3 WebMVC Java 21 real native executable canary passes"));
        assertTrue(roadmap.contains("broader real-application AOT/native coverage remains future hardening work"));
        assertFalse(
                roadmap.contains("AOT/native processing remains future work."),
                "Roadmap must distinguish the implemented Spring Boot native canary path from broader future hardening");
    }

    @Test
    void springBootNativeDocsStayNarrowAfterRealAppSmokeExists() throws IOException {
        String frameworkReadiness = Files.readString(RepositoryPaths.root().resolve("docs/framework-readiness.md"));
        String nativeGraalvm = Files.readString(RepositoryPaths.root().resolve("docs/native-graalvm.md"));
        String springBootReadiness = Files.readString(RepositoryPaths.root().resolve("docs/spring-boot-readiness.md"));

        assertTrue(frameworkReadiness.contains("not broad Spring native-image support"));
        assertTrue(frameworkReadiness.contains("Spring Boot 3.3 WebMVC HTTP app on Java 21"));
        assertTrue(nativeGraalvm.contains("not broad real-app native-image support"));
        assertTrue(nativeGraalvm.contains("Spring Boot 3.3 WebMVC Java 21 smoke"));
        assertTrue(springBootReadiness.contains("This is canary support, not broad real-app native-image support"));
        assertTrue(springBootReadiness.contains("proven Spring Boot 3.3 WebMVC Java 21 shape until broader fixtures pass"));
    }

    @Test
    void vertxPostgresReadinessStaysSpecificUntilSmokesExist() throws IOException {
        String frameworkReadiness = Files.readString(RepositoryPaths.root().resolve("docs/framework-readiness.md"));
        String nativeGraalvm = Files.readString(RepositoryPaths.root().resolve("docs/native-graalvm.md"));
        String vertxReadiness = Files.readString(RepositoryPaths.root().resolve("docs/vertx-readiness.md"));

        assertTrue(frameworkReadiness.contains("Vert.x PostgreSQL CRUD still needs DB-backed JVM smoke evidence and a native smoke"));
        assertTrue(frameworkReadiness.contains("should not be claimed until the real JVM and native smokes pass against PostgreSQL"));
        assertTrue(nativeGraalvm.contains("not claimed until `scripts/smoke-vertx-postgres-crud` and `scripts/smoke-vertx-postgres-native` exist and pass"));
        assertTrue(vertxReadiness.contains("## PostgreSQL CRUD Readiness Target"));
        assertTrue(vertxReadiness.contains("`io.vertx:vertx-web`"));
        assertTrue(vertxReadiness.contains("`io.vertx:vertx-pg-client`"));
        assertTrue(vertxReadiness.contains("The fixture project now exists with Vert.x Web routes"));
        assertTrue(vertxReadiness.contains("`scripts/smoke-vertx-postgres-crud` also exists and fails early with setup guidance"));
        assertTrue(vertxReadiness.contains("ZOLT_VERTX_POSTGRES_SMOKE_ZOLT=scripts/bootstrap-zolt-jvm scripts/smoke-vertx-postgres-crud"));
        assertTrue(vertxReadiness.contains("ZOLT_VERTX_POSTGRES_NATIVE_SMOKE_ZOLT=scripts/bootstrap-zolt-jvm scripts/smoke-vertx-postgres-native"));
        assertTrue(vertxReadiness.contains("The smoke must run a real executable; building a binary without launching it is not enough."));
        assertFalse(
                vertxReadiness.contains("broad Vert.x native-image support"),
                "Vert.x readiness must not broaden the native-image claim before database-backed smokes pass");
    }
}
