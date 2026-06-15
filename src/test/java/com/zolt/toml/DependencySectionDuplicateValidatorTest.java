package com.zolt.toml;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DependencySectionDuplicateValidatorTest {
    @Test
    void rejectsDuplicateCoordinateAcrossMainDependencySections() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> DependencySectionDuplicateValidator.validateNoDuplicateMainDependencyCoordinates(
                        declarations(Map.of("com.acme:contract", "1.0.0"), Set.of(), Map.of()),
                        declarations(Map.of("com.acme:contract", "1.0.0"), Set.of(), Map.of()),
                        declarations(Map.of(), Set.of(), Map.of()),
                        declarations(Map.of(), Set.of(), Map.of()),
                        declarations(Map.of(), Set.of(), Map.of())));

        assertEquals(
                "Dependency com.acme:contract is declared in both [api.dependencies] and [dependencies]. Keep it in one section.",
                exception.getMessage());
    }

    @Test
    void rejectsDuplicatesAcrossManagedAndWorkspaceDeclarations() {
        ZoltConfigException exception = assertThrows(
                ZoltConfigException.class,
                () -> DependencySectionDuplicateValidator.validateNoDuplicateMainDependencyCoordinates(
                        declarations(Map.of(), Set.of(), Map.of()),
                        declarations(Map.of(), Set.of("com.acme:shared"), Map.of()),
                        declarations(Map.of(), Set.of(), Map.of()),
                        declarations(Map.of(), Set.of(), Map.of("com.acme:shared", "../shared")),
                        declarations(Map.of(), Set.of(), Map.of())));

        assertEquals(
                "Dependency com.acme:shared is declared in both [dependencies] and [provided.dependencies]. Keep it in one section.",
                exception.getMessage());
    }

    @Test
    void acceptsDistinctCoordinatesAcrossMainDependencySections() {
        assertDoesNotThrow(() -> DependencySectionDuplicateValidator.validateNoDuplicateMainDependencyCoordinates(
                declarations(Map.of("com.acme:api", "1.0.0"), Set.of(), Map.of()),
                declarations(Map.of("com.acme:impl", "1.0.0"), Set.of(), Map.of()),
                declarations(Map.of("com.acme:runtime", "1.0.0"), Set.of(), Map.of()),
                declarations(Map.of(), Set.of("com.acme:provided"), Map.of()),
                declarations(Map.of(), Set.of(), Map.of("com.acme:dev", "../dev"))));
    }

    private static DependencySectionCodec.DependencyDeclarations declarations(
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace) {
        return new DependencySectionCodec.DependencyDeclarations(versioned, managed, workspace);
    }
}
