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
import org.junit.jupiter.api.Test;

/**
 * Enforces the actionable-error invariant: the carrier keeps its required non-blank remediation
 * field and its {@code summary + ' ' + remediation} message contract, and the migrated high-traffic
 * sites construct the carrier. The scan stays bounded to the carrier plus the migrated sites so it
 * never regresses on the still-unmigrated flat-string errors.
 */
final class ActionableErrorContractTest {
    private static final Path MODEL_ERROR_ROOT = RepositoryPaths.root()
            .resolve("modules/zolt-model/src/main/java/com/zolt/error");
    private static final List<Path> MIGRATED_SITES = List.of(
            RepositoryPaths.root().resolve("modules/zolt-toml/src/main/java/com/zolt/toml/ZoltTomlParser.java"),
            RepositoryPaths.root().resolve(
                    "modules/zolt-build/src/main/java/com/zolt/build/springboot/SpringBootNativeBoundaryDiagnostics.java"));

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
                    source.contains("ActionableError.of("),
                    () -> RepositoryPaths.displayPath(site) + " must construct ActionableError for its user errors.");
        }
    }
}
