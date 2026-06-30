package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.error.ActionableError;
import com.zolt.error.ActionableException;
import com.zolt.error.HasActionableError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Enforces the actionable-error invariant: the carrier keeps its required non-blank remediation
 * field and its {@code summary + ' ' + remediation} message contract, the migrated high-traffic
 * sites construct the carrier, and every domain exception that adopts the carrier is registered so
 * carrier adoption stays explicit and growable (a user-facing exception cannot silently regress to a
 * flat-string-only message, and a new carrier adopter must be registered deliberately).
 */
final class ActionableErrorContractTest {
    private static final Path MODEL_ERROR_ROOT = RepositoryPaths.root()
            .resolve("modules/zolt-model/src/main/java/com/zolt/error");
    private static final List<Path> MIGRATED_SITES = List.of(
            RepositoryPaths.root().resolve("modules/zolt-toml/src/main/java/com/zolt/toml/ZoltTomlParser.java"),
            RepositoryPaths.root().resolve(
                    "modules/zolt-build/src/main/java/com/zolt/build/springboot/SpringBootNativeBoundaryDiagnostics.java"),
            RepositoryPaths.root().resolve(
                    "modules/zolt-resolve/src/main/java/com/zolt/resolve/lockfile/persistence/ResolveLockfilePersistence.java"),
            RepositoryPaths.root().resolve(
                    "modules/zolt-resolve/src/main/java/com/zolt/resolve/materialization/session/RepositoryAccessPlanner.java"),
            RepositoryPaths.root().resolve(
                    "modules/zolt-resolve/src/main/java/com/zolt/resolve/request/DirectDependencyRequestPlanner.java"),
            RepositoryPaths.root().resolve(
                    "modules/zolt-resolve/src/main/java/com/zolt/resolve/materialization/session/RepositoryFetchCoordinator.java"),
            RepositoryPaths.root().resolve("modules/zolt-build/src/main/java/com/zolt/build/compile/JavacRunner.java"),
            RepositoryPaths.root().resolve("modules/zolt-build/src/main/java/com/zolt/build/BuildService.java"),
            RepositoryPaths.root().resolve(
                    "modules/zolt-toml/src/main/java/com/zolt/lockfile/toml/ZoltLockfileReader.java"),
            RepositoryPaths.root().resolve("modules/zolt-toml/src/main/java/com/zolt/toml/support/TomlVersions.java"),
            RepositoryPaths.root().resolve(
                    "modules/zolt-workspace/src/main/java/com/zolt/workspace/service/WorkspaceBuildService.java"));

    /**
     * User-facing domain exceptions that carry an {@link HasActionableError} so the CLI renderer always
     * emits a "Next:" remediation line for them. New user-facing exceptions should join this registry and
     * implement {@code HasActionableError}; the discovery test below keeps the registry complete.
     */
    private static final List<Path> CARRIER_EXCEPTIONS = List.of(
            RepositoryPaths.root().resolve("modules/zolt-toml/src/main/java/com/zolt/toml/ZoltConfigException.java"),
            RepositoryPaths.root().resolve("modules/zolt-toml/src/main/java/com/zolt/lockfile/toml/LockfileReadException.java"),
            RepositoryPaths.root().resolve("modules/zolt-resolve/src/main/java/com/zolt/resolve/ResolveException.java"),
            RepositoryPaths.root().resolve("modules/zolt-build/src/main/java/com/zolt/build/BuildException.java"),
            RepositoryPaths.root().resolve("modules/zolt-build/src/main/java/com/zolt/build/JavacException.java"),
            RepositoryPaths.root().resolve("modules/zolt-build/src/main/java/com/zolt/build/PackageException.java"),
            RepositoryPaths.root().resolve(
                    "modules/zolt-build/src/main/java/com/zolt/build/nativeimage/NativeImageException.java"));

    @Test
    void carrierKeepsRequiredNonBlankRemediationField() throws IOException {
        Path carrier = MODEL_ERROR_ROOT.resolve("ActionableError.java");
        assertTrue(Files.isRegularFile(carrier), () -> "ActionableError must live in zolt-model at " + carrier);
        String source = Files.readString(carrier);

        assertTrue(
                source.contains("record ActionableError("),
                "ActionableError must remain an immutable record carrier.");
        assertTrue(
                source.contains("String summary") && source.contains("String remediation"),
                "ActionableError must keep its summary and remediation fields.");
        assertTrue(
                source.contains("remediation.isBlank()"),
                "ActionableError must validate that remediation is non-blank.");
    }

    @Test
    void carrierRejectsBlankRemediationAtRuntime() {
        assertThrows(IllegalArgumentException.class, () -> ActionableError.of("Something failed.", "   "));
        assertThrows(IllegalArgumentException.class, () -> ActionableError.of("Something failed.", ""));
        assertThrows(IllegalArgumentException.class, () -> ActionableError.of("   ", "Do the thing."));
    }

    @Test
    void carrierMessageContractIsSummaryThenRemediation() {
        ActionableError error = ActionableError.of("Could not read zolt.toml.", "Check that the file exists.");

        assertEquals("Could not read zolt.toml. Check that the file exists.", error.message());
        assertEquals(error.message(), new ActionableException(error).getMessage());
        assertTrue(new ActionableException(error) instanceof HasActionableError);
        assertEquals(error, new ActionableException(error).error());
    }

    @Test
    void migratedSitesConstructTheCarrier() throws IOException {
        for (Path site : MIGRATED_SITES) {
            assertTrue(Files.isRegularFile(site), () -> "Expected migrated error site at " + site);
            String source = Files.readString(site);
            assertTrue(
                    source.contains("ActionableError.of(") || source.contains(".actionable("),
                    () -> RepositoryPaths.displayPath(site)
                            + " must construct an ActionableError (directly or via an exception's actionable(...) factory) "
                            + "for its user errors.");
        }
    }

    @Test
    void registeredCarrierExceptionsImplementHasActionableError() throws IOException {
        for (Path exception : CARRIER_EXCEPTIONS) {
            assertTrue(Files.isRegularFile(exception), () -> "Expected user-facing exception at " + exception);
            assertTrue(
                    Files.readString(exception).contains("HasActionableError"),
                    () -> RepositoryPaths.displayPath(exception)
                            + " must implement HasActionableError so the CLI renders a remediation line for it.");
        }
    }

    @Test
    void everyDomainExceptionAdoptingTheCarrierIsRegistered() throws IOException {
        Path root = RepositoryPaths.root();
        Set<String> registered = new TreeSet<>();
        for (Path exception : CARRIER_EXCEPTIONS) {
            registered.add(relative(root, exception));
        }
        Set<String> discovered = new TreeSet<>();
        for (String area : List.of("modules", "apps")) {
            Path base = root.resolve(area);
            if (!Files.isDirectory(base)) {
                continue;
            }
            List<Path> candidates;
            try (Stream<Path> files = Files.walk(base)) {
                candidates = files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith("Exception.java"))
                        .filter(path -> relative(root, path).contains("/src/main/"))
                        .filter(path -> !relative(root, path).contains("/com/zolt/error/"))
                        .toList();
            }
            for (Path file : candidates) {
                if (Files.readString(file).contains("HasActionableError")) {
                    discovered.add(relative(root, file));
                }
            }
        }
        assertEquals(
                registered,
                discovered,
                "Every domain *Exception that implements HasActionableError must be listed in CARRIER_EXCEPTIONS "
                        + "so carrier adoption stays explicit and a registered adopter cannot silently drop the carrier.");
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }
}
