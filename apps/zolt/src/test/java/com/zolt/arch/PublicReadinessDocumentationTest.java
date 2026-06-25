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
                readme.contains("documented WebMVC, PetClinic-style medium app, and enterprise canary paths"),
                "README should mention the promoted Spring Boot JVM support surface");
        assertTrue(
                readme.contains("recurring Spring Boot 3.3 Java 21 fixture family"),
                "README should mention the recurring bounded Spring Boot native fixture family");
        assertTrue(
                readme.contains("WebMVC baseline, Actuator, WebMVC contract, and Spring JDBC/H2 data-access rows through native Zolt"),
                "README should list the recurring Spring Boot native rows");
        assertTrue(
                readme.contains("not arbitrary Spring native-image support"),
                "README should not over-claim Spring Boot native-image support");
        assertTrue(
                readme.contains("Spring Boot 4, Spring Cloud, enterprise native images, external database native topologies, container images, and Maven/Gradle plugin behavior remain unclaimed"),
                "README should keep unsupported Spring native areas explicit");
    }

    @Test
    void adoptionReadinessListsPublicSupportSurfaceBoundaries() throws IOException {
        String readiness = Files.readString(RepositoryPaths.root().resolve("docs/adoption-readiness.md"));

        assertTrue(readiness.contains("## Public Support Surface"));
        assertTrue(readiness.contains("| Plain Java applications | Supported |"));
        assertTrue(readiness.contains("| Spring Boot JVM applications | Supported | BOM-managed WebMVC apps, PetClinic-style medium apps, and the public-safe enterprise canary"));
        assertTrue(readiness.contains("| Spring Boot AOT/native | Supported bounded fixture family, not arbitrary Spring native support |"));
        assertTrue(readiness.contains("recurring Spring Boot 3.3 Java 21 native fixture family covers WebMVC baseline, Actuator, WebMVC contract, and Spring JDBC/H2 data-access rows through native Zolt"));
        assertTrue(readiness.contains("| Quarkus JVM applications | Supported opt-in fixture |"));
        assertTrue(readiness.contains("ZOLT_ADOPTION_INCLUDE_QUARKUS=1 scripts/smoke-adoption-easy-medium"));
        assertTrue(readiness.contains(
                "Dev mode, native images, test resources/profiles, integration/main tests, and Maven/Gradle plugin behavior remain unsupported"));
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
    void commandDocsDescribeUberMergeDecisionEvidence() throws IOException {
        String commands = Files.readString(RepositoryPaths.root().resolve("docs/commands.md"));

        assertTrue(commands.contains("package evidence also records `uberMergeDecisions`"));
        assertTrue(commands.contains("service descriptor merges"));
        assertTrue(commands.contains("Netty version metadata merges"));
        assertTrue(commands.contains("relocated dependency license/notice/dependencies metadata"));
        assertTrue(commands.contains("omitted dependency module descriptors"));
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
    void frameworkReadinessBoundsQuarkusAnnotationTests() throws IOException {
        String frameworkReadiness = Files.readString(RepositoryPaths.root().resolve("docs/framework-readiness.md"));

        assertTrue(frameworkReadiness.contains("| Quarkus | Supported opt-in JVM fixture |"));
        assertTrue(frameworkReadiness.contains("run the REST Assured `@QuarkusTest` fixture through public `zolt test`"));
        assertTrue(frameworkReadiness.contains("Quarkus test resources/profiles"));
        assertTrue(frameworkReadiness.contains("resource-family"));
        assertFalse(
                frameworkReadiness.contains("arbitrary Quarkus `@QuarkusTest` support"),
                "Framework readiness must keep the Quarkus annotation-test claim fixture-bounded");
    }

    @Test
    void readmeBoundsPromotedQuarkusJvmSupport() throws IOException {
        String readme = Files.readString(RepositoryPaths.root().resolve("README.md"));

        assertTrue(readme.contains("Supported opt-in JVM fixture for the documented Quarkus 3.33 HTTP shape"));
        assertTrue(readme.contains("REST Assured direct `@QuarkusTest`"));
        assertTrue(readme.contains(
                "Quarkus dev mode, native images, test resources/profiles, integration/main tests, and Maven/Gradle plugin behavior remain explicitly unsupported"));
        assertFalse(
                readme.contains("| Quarkus | Experimental JVM build/test/package coverage"),
                "README should not keep the stale experimental Quarkus JVM wording after the opt-in fixture promotion");
    }

    @Test
    void adoptionSmokeExposesQuarkusSpecificOptIn() throws IOException {
        String smoke = Files.readString(RepositoryPaths.root().resolve("scripts/smoke-adoption-easy-medium"));

        assertTrue(smoke.contains("ZOLT_ADOPTION_INCLUDE_QUARKUS"));
        assertTrue(smoke.contains("ZOLT_ADOPTION_INCLUDE_EXPERIMENTAL"));
        assertTrue(smoke.contains("INCLUDE_QUARKUS"));
        assertTrue(smoke.contains("quarkus opt-in fixture skipped"));
        assertFalse(
                smoke.contains("quarkus-experimental"),
                "Adoption smoke labels should use the promoted Quarkus fixture name");
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
    void springBootNativeDocsStayBoundedAfterM26Evidence() throws IOException {
        String frameworkReadiness = Files.readString(RepositoryPaths.root().resolve("docs/framework-readiness.md"));
        String nativeGraalvm = Files.readString(RepositoryPaths.root().resolve("docs/native-graalvm.md"));
        String springBootReadiness = Files.readString(RepositoryPaths.root().resolve("docs/spring-boot-readiness.md"));

        assertTrue(frameworkReadiness.contains("not broad Spring native-image support"));
        assertTrue(frameworkReadiness.contains("bounded M26 fixture family"));
        assertTrue(frameworkReadiness.contains("WebMVC, Actuator, WebMVC contract, and Spring JDBC/H2 data-access rows"));
        assertTrue(frameworkReadiness.contains("Native support-boundary diagnostics reject selected unproven ecosystem shapes"));
        assertTrue(frameworkReadiness.contains("Spring Cloud native applications"));
        assertTrue(frameworkReadiness.contains("external database native topologies with PostgreSQL, MySQL, MariaDB, or SQL Server drivers"));
        assertTrue(nativeGraalvm.contains("not arbitrary Spring native-image support"));
        assertTrue(nativeGraalvm.contains("WebMVC, Actuator, WebMVC contract, and Spring JDBC/H2 data-access rows through native Zolt"));
        assertTrue(springBootReadiness.contains("bounded to the proven Spring Boot 3.3 Java 21 M26 fixture family"));
        assertTrue(springBootReadiness.contains("Projects outside the WebMVC baseline, Actuator, WebMVC contract, and Spring JDBC/H2 data-access rows"));
        assertTrue(springBootReadiness.contains("declare Spring Cloud dependencies or external database drivers"));
        assertTrue(springBootReadiness.contains("Ordinary Spring Boot JVM build, test, run, and executable jar workflows are not rejected"));
    }

    @Test
    void mediumSpringBootJvmAuditNamesCoveredMissingAndUnsupportedShapes() throws IOException {
        String springBootReadiness = Files.readString(RepositoryPaths.root().resolve("docs/spring-boot-readiness.md"));
        String milestones = Files.readString(RepositoryPaths.root().resolve("followUps/MILESTONES.md"));

        assertTrue(springBootReadiness.contains("## Medium Spring Boot JVM Coverage Audit"));
        assertTrue(springBootReadiness.contains("single-module Spring Boot 4.0.6 application"));
        assertTrue(springBootReadiness.contains("Implemented and covered by `examples/spring-boot-petclinic-lite`"));
        assertTrue(springBootReadiness.contains("Spring Boot BOM-managed starter dependencies"));
        assertTrue(springBootReadiness.contains("Thymeleaf, and validation"));
        assertTrue(springBootReadiness.contains("nested generated frontend/static assets and WebJar-style resources"));
        assertTrue(springBootReadiness.contains("launched HTTP probes for the home controller/template path"));
        assertTrue(springBootReadiness.contains("executable jar assertions for templates, SQL initialization resources"));
        assertTrue(springBootReadiness.contains("native-Zolt proof for the same Spring Boot JVM workflow"));
        assertTrue(springBootReadiness.contains("remaining PetClinic-class gaps are broader enterprise and deployment"));
        assertTrue(springBootReadiness.contains("Intentionally unsupported by this medium JVM audit"));
        assertTrue(springBootReadiness.contains("Spring native-image support for the full PetClinic-style fixture"));
        assertTrue(springBootReadiness.contains("Maven or Gradle plugin execution, frontend plugin execution"));
        assertTrue(springBootReadiness.contains("lifecycle compatibility, profiles, or compatibility mode"));
        assertTrue(milestones.contains(" — Add medium Spring Boot frontend resource depth"));
        assertTrue(milestones.contains(" — Add medium Spring Boot HTTP and package evidence"));
    }

    @Test
    void m26SpringBootNativeMilestoneRequiresExecutableEvidenceBeforePromotion() throws IOException {
        String springBootReadiness = Files.readString(RepositoryPaths.root().resolve("docs/spring-boot-readiness.md"));
        String nativeGraalvm = Files.readString(RepositoryPaths.root().resolve("docs/native-graalvm.md"));
        String milestone = Files.readString(RepositoryPaths.root().resolve(
                "followUps/-design-broader-spring-boot-native-readiness-milestone.md"));

        assertTrue(springBootReadiness.contains("## Broader Spring Boot Native Milestone"));
        assertTrue(springBootReadiness.contains("M26 tracks the work that moved Zolt from one narrow Spring Boot native canary"));
        assertTrue(springBootReadiness.contains("Spring Boot 3.3 WebMVC on Java 21"));
        assertTrue(springBootReadiness.contains("Proven: the Spring Boot 3.3 Java 21 M26 native fixture family"));
        assertTrue(springBootReadiness.contains("| Fixture row | Baseline | JVM evidence | AOT evidence | Fake-native evidence | Real-native evidence | Native-Zolt evidence | Promotion role |"));
        assertTrue(springBootReadiness.contains("| WebMVC plus Actuator | Spring Boot 3.3 on Java 21 |"));
        assertTrue(springBootReadiness.contains("`scripts/smoke-spring-boot-native-actuator` launches the executable and probes app plus Actuator endpoints"));
        assertTrue(springBootReadiness.contains("Included in the bounded M26 Spring native support family"));
        assertTrue(springBootReadiness.contains("| WebMVC contract | Spring Boot 3.3 on Java 21 |"));
        assertTrue(springBootReadiness.contains("`scripts/smoke-spring-boot-native-web-contract` launches the executable and probes JSON, validation, configuration, and static resources"));
        assertTrue(springBootReadiness.contains("| Data access | Spring Boot 3.3 on Java 21 |"));
        assertTrue(springBootReadiness.contains("`scripts/smoke-spring-boot-native-data-access` launches the executable and probes create/read persistence behavior"));
        assertTrue(springBootReadiness.contains("Unsupported until proven: arbitrary Spring native-image support, Spring Boot 4 native support"));
        assertTrue(springBootReadiness.contains("Spring Boot 4 native support remains unsupported until a compatible JDK/Native Image toolchain is proven."));
        assertTrue(springBootReadiness.contains("Promotion rule: public docs can claim the bounded M26 Spring native fixture family"));
        assertTrue(springBootReadiness.contains(" promotes public readiness docs only after the M26 real executable smokes pass."));
        assertTrue(nativeGraalvm.contains("M26 is the completed broader Spring Boot native readiness milestone"));
        assertTrue(nativeGraalvm.contains("scripts/smoke-spring-boot-native-actuator"));
        assertTrue(nativeGraalvm.contains("scripts/smoke-spring-boot-native-web-contract"));
        assertTrue(nativeGraalvm.contains("deterministic validation failure JSON"));
        assertTrue(nativeGraalvm.contains("scripts/smoke-spring-boot-native-data-access"));
        assertTrue(nativeGraalvm.contains("H2 as a runtime-only embedded database dependency"));
        assertTrue(nativeGraalvm.contains("writes `spring-aot-evidence.json` beside `native-image.log`"));
        assertTrue(nativeGraalvm.contains("reflection metadata, optional reachability metadata, and fingerprint evidence"));
        assertTrue(nativeGraalvm.contains("it merges that entry into Spring's `reflect-config.json` instead of overwriting existing Spring-generated reflection metadata"));
        assertTrue(nativeGraalvm.contains("fails before Native Image if required AOT output or reflection/native metadata is missing"));
        assertTrue(nativeGraalvm.contains("bounded Spring Boot 3.3 Java 21 M26 fixture family"));
        assertTrue(nativeGraalvm.contains("Public wording should stay limited to that evidence."));
        assertTrue(milestone.contains(": design the Spring Boot native fixture matrix."));
        assertTrue(milestone.contains(": promote public readiness docs only after the M26 real executable smokes pass."));
        assertFalse(
                springBootReadiness.contains("M26 broadens the public Spring native claim"),
                "M26 docs must not broaden the claim without bounding it to the executable smoke evidence");
        assertFalse(
                springBootReadiness.contains("Planned: a broader Spring Boot 3.3 Java 21 native fixture matrix"),
                "M26 docs must not describe the completed expanded fixture matrix as merely planned");
    }

    @Test
    void m27SpringSupportMilestoneDependsOnEvidenceAndKeepsUnsupportedAreasExplicit() throws IOException {
        String frameworkReadiness = Files.readString(RepositoryPaths.root().resolve("docs/framework-readiness.md"));
        String nativeGraalvm = Files.readString(RepositoryPaths.root().resolve("docs/native-graalvm.md"));
        String springBootReadiness = Files.readString(RepositoryPaths.root().resolve("docs/spring-boot-readiness.md"));
        String milestone = Files.readString(RepositoryPaths.root().resolve(
                "followUps/-design-broader-spring-boot-and-spring-native-support-milestone.md"));

        assertTrue(springBootReadiness.contains("## Broader Spring Boot And Spring Native Support Milestone"));
        assertTrue(springBootReadiness.contains("M27 is the follow-on milestone after M26."));
        assertTrue(springBootReadiness.contains("M27 now has enough evidence to promote broader Spring Boot JVM readiness"));
        assertTrue(springBootReadiness.contains("M27 promoted fixture families:"));
        assertTrue(springBootReadiness.contains("| PetClinic-style single module |"));
        assertTrue(springBootReadiness.contains("| Enterprise single service |"));
        assertTrue(springBootReadiness.contains("| Spring native web service |"));
        assertTrue(springBootReadiness.contains("| Spring ecosystem boundary fixtures |"));
        assertTrue(springBootReadiness.contains("Spring Boot 4 native support and any Spring/JDK combination beyond the proven toolchain."));
        assertTrue(springBootReadiness.contains("Maven or Gradle plugin execution, lifecycle compatibility, profiles, or compatibility mode."));
        assertTrue(springBootReadiness.contains(" audited medium Spring Boot JVM fixture gaps."));
        assertTrue(springBootReadiness.contains(" added medium Spring Boot frontend resource depth."));
        assertTrue(springBootReadiness.contains(" added medium Spring Boot HTTP and package evidence."));
        assertTrue(springBootReadiness.contains(" promotes broader Spring readiness docs from that evidence."));
        assertTrue(nativeGraalvm.contains("M27 promoted the evidence-backed JVM Spring Boot surface"));
        assertTrue(frameworkReadiness.contains("M27 promoted the evidence-backed JVM Spring Boot surface"));
        assertTrue(milestone.contains("M27 - Broader Spring Boot and Spring native support"));
        assertTrue(milestone.contains("Keep M26 as the prerequisite proof before any broader Spring native public claim."));
        assertTrue(milestone.contains(": audit medium Spring Boot JVM fixture gaps."));
        assertTrue(milestone.contains(": promote broader Spring readiness docs after M27 evidence."));
        assertFalse(
                springBootReadiness.contains("M27 broadly supports Spring native"),
                "M27 docs must not claim broad Spring native support before planned evidence exists");
    }

    @Test
    void springNativeFixtureFamilyAfterM26NamesRecurringRowsAndRejectedShapes() throws IOException {
        String nativeGraalvm = Files.readString(RepositoryPaths.root().resolve("docs/native-graalvm.md"));
        String springBootReadiness = Files.readString(RepositoryPaths.root().resolve("docs/spring-boot-readiness.md"));

        assertTrue(springBootReadiness.contains("## Recurring Spring Native Fixture Family After M26"));
        assertTrue(springBootReadiness.contains("Only rows with real Spring AOT output, real Native Image execution"));
        assertTrue(springBootReadiness.contains("| WebMVC baseline |"));
        assertTrue(springBootReadiness.contains("| WebMVC plus Actuator |"));
        assertTrue(springBootReadiness.contains("| WebMVC contract |"));
        assertTrue(springBootReadiness.contains("| Spring JDBC/H2 data access |"));
        assertTrue(springBootReadiness.contains("PetClinic-style medium JVM fixture native images"));
        assertTrue(springBootReadiness.contains("Enterprise Spring Boot native images"));
        assertTrue(springBootReadiness.contains("Spring Boot 4 native support or any Spring/JDK combination beyond Spring Boot"));
        assertTrue(springBootReadiness.contains("Spring Cloud native applications, external database native topologies"));
        assertTrue(springBootReadiness.contains("Support-boundary diagnostics should be added before implementation"));
        assertTrue(nativeGraalvm.contains("M27 keeps the M26 rows as the recurring Spring native fixture family after M26"));
        assertTrue(nativeGraalvm.contains("WebMVC baseline, WebMVC plus Actuator, WebMVC contract, and Spring JDBC/H2 data access"));
        assertTrue(nativeGraalvm.contains("PetClinic-style medium JVM native images, enterprise Spring Boot native images"));
    }

    @Test
    void enterpriseSpringBootBoundarySeparatesZoltPrimitivesFromBuildToolCompatibility() throws IOException {
        String blueprint = Files.readString(RepositoryPaths.root().resolve("docs/enterprise-spring-boot-blueprint.md"));
        String normalizedBlueprint = blueprint.replaceAll("\\s+", " ");

        assertTrue(blueprint.contains("## Support Boundary Audit"));
        assertTrue(blueprint.contains("Implemented Enterprise JVM Primitives"));
        assertTrue(blueprint.contains("Authenticated Maven-compatible repositories with redaction-safe credential"));
        assertTrue(blueprint.contains("Typed OpenAPI generated-source execution before Java compilation"));
        assertTrue(blueprint.contains("Spring Boot executable WAR packaging with package evidence"));
        assertTrue(blueprint.contains("Static `zolt explain` and focused blocker reports for a redacted Gradle"));
        assertTrue(blueprint.contains("Publish dry-run routing for the selected Spring Boot WAR artifact"));
        assertTrue(blueprint.contains("Explicitly Unclaimed"));
        assertTrue(blueprint.contains("Executing Gradle tasks, Gradle plugins, Maven plugins"));
        assertTrue(blueprint.contains("Enterprise-shaped Spring native images"));
        assertTrue(blueprint.contains("Spring Cloud, external database native topologies, container image assembly"));
        assertTrue(blueprint.contains("Broad OpenAPI generator/plugin parity"));
        assertTrue(normalizedBlueprint.contains(
                "It should not say that Zolt can run or emulate the source Gradle or Maven build."));
    }

    @Test
    void vertxPostgresReadinessStaysSpecificUntilSmokesExist() throws IOException {
        String frameworkReadiness = Files.readString(RepositoryPaths.root().resolve("docs/framework-readiness.md"));
        String nativeGraalvm = Files.readString(RepositoryPaths.root().resolve("docs/native-graalvm.md"));
        String vertxReadiness = Files.readString(RepositoryPaths.root().resolve("docs/vertx-readiness.md"));

        assertTrue(frameworkReadiness.contains("Vert.x PostgreSQL CRUD still needs DB-backed JVM and native smoke evidence before readiness is claimed"));
        assertTrue(frameworkReadiness.contains("should not be claimed until the real JVM and native smokes pass against PostgreSQL"));
        assertTrue(nativeGraalvm.contains("CRUD, validation, response-header, cleanup, and real-executable probes"));
        assertTrue(nativeGraalvm.contains("not claimed until both scripts pass against a real PostgreSQL database"));
        assertTrue(nativeGraalvm.contains("PostgreSQL SCRAM/SASL authentication is present on JVM, packaged, and native runtime classpaths"));
        assertTrue(nativeGraalvm.contains("the current smoke script launches the executable, and the remaining promotion evidence is a real database-backed run"));
        assertTrue(vertxReadiness.contains("## PostgreSQL CRUD Readiness Target"));
        assertTrue(vertxReadiness.contains("`io.vertx:vertx-web`"));
        assertTrue(vertxReadiness.contains("`io.vertx:vertx-pg-client`"));
        assertTrue(vertxReadiness.contains("`com.ongres.scram:client` as an explicit runtime dependency"));
        assertTrue(vertxReadiness.contains("The fixture project now exists with Vert.x Web routes"));
        assertTrue(vertxReadiness.contains("explicit SCRAM runtime authentication support"));
        assertTrue(vertxReadiness.contains("`scripts/smoke-vertx-postgres-crud` and `scripts/smoke-vertx-postgres-native` also exist and fail early with setup guidance"));
        assertTrue(vertxReadiness.contains("Those smokes capture response headers, require JSON-bearing responses to declare `content-type: application/json` and `cache-control: no-store`, and drop their external database table during cleanup when `psql` is available."));
        assertTrue(vertxReadiness.contains("A local build check has produced a real `vertx-postgres-crud` Native Image executable"));
        assertTrue(vertxReadiness.contains("Woodpecker has a manual heavy pipeline for the database-backed gates"));
        assertTrue(vertxReadiness.contains("--initialize-at-run-time=io.netty.channel,io.netty.handler.ssl"));
        assertTrue(vertxReadiness.contains("ZOLT_VERTX_POSTGRES_SMOKE_ZOLT=scripts/bootstrap-zolt-jvm scripts/smoke-vertx-postgres-crud"));
        assertTrue(vertxReadiness.contains("ZOLT_VERTX_POSTGRES_NATIVE_SMOKE_ZOLT=scripts/bootstrap-zolt-jvm scripts/smoke-vertx-postgres-native"));
        assertTrue(vertxReadiness.contains("The smoke must run a real executable; building a binary without launching it is not enough."));
        assertFalse(
                vertxReadiness.contains("broad Vert.x native-image support"),
                "Vert.x readiness must not broaden the native-image claim before database-backed smokes pass");
    }
}
