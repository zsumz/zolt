package com.zolt.resolve.metadata.pom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.maven.Coordinate;
import com.zolt.maven.EffectiveRawPom;
import com.zolt.maven.RawPom;
import com.zolt.maven.RawPomDependency;
import com.zolt.resolve.ResolveException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ImportedBomDependencyManagementExpanderTest {
    private final ImportedBomDependencyManagementExpander expander =
            new ImportedBomDependencyManagementExpander();

    @Test
    void expandsImportedBomDependenciesAndPreservesNonBomEntries() {
        EffectiveRawPom root = pom(
                "com.example",
                "app-bom",
                "1.0.0",
                Map.of("imported.version", "2.0.0"),
                List.of(
                        dependency("com.example", "direct-managed", "1.0.0", Optional.empty(), Optional.empty(), Optional.empty()),
                        dependency("com.example", "imported-bom", "${imported.version}", Optional.of("import"), Optional.of("pom"), Optional.empty()),
                        dependency("com.example", "classified", "1.0.0", Optional.empty(), Optional.empty(), Optional.of("tests"))));
        EffectiveRawPom imported = pom(
                "com.example",
                "imported-bom",
                "2.0.0",
                Map.of("lib.version", "3.0.0"),
                List.of(
                        dependency("com.example", "lib", "${lib.version}", Optional.empty(), Optional.empty(), Optional.empty()),
                        dependency("com.example", "provided-lib", "${missing.version}", Optional.of("provided"), Optional.empty(), Optional.empty()),
                        dependency("com.example", "test-lib", "${missing.version}", Optional.of("test"), Optional.empty(), Optional.empty()),
                        dependency("com.example", "classified-import", "4.0.0", Optional.empty(), Optional.empty(), Optional.of("tests"))));
        AtomicReference<Coordinate> loadedCoordinate = new AtomicReference<>();

        List<RawPomDependency> expanded = expander.expand(
                root,
                List.of("com.example:app-bom:1.0.0"),
                (coordinate, importStack) -> {
                    loadedCoordinate.set(coordinate);
                    assertEquals(List.of("com.example:app-bom:1.0.0"), importStack);
                    return imported;
                });

        assertEquals(new Coordinate("com.example", "imported-bom", Optional.of("2.0.0")), loadedCoordinate.get());
        assertEquals(List.of(
                dependency("com.example", "direct-managed", "1.0.0", Optional.empty(), Optional.empty(), Optional.empty()),
                dependency("com.example", "lib", "3.0.0", Optional.empty(), Optional.empty(), Optional.empty()),
                dependency("com.example", "classified-import", "4.0.0", Optional.empty(), Optional.empty(), Optional.of("tests")),
                dependency("com.example", "classified", "1.0.0", Optional.empty(), Optional.empty(), Optional.of("tests"))), expanded);
    }

    @Test
    void failsClearlyWhenImportedBomVersionIsMissing() {
        EffectiveRawPom root = pom(
                "com.example",
                "app-bom",
                "1.0.0",
                Map.of(),
                List.of(dependency("com.example", "imported-bom", null, Optional.of("import"), Optional.of("pom"), Optional.empty())));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> expander.expand(
                        root,
                        List.of("com.example:app-bom:1.0.0"),
                        (coordinate, importStack) -> {
                            throw new AssertionError("unexpected imported BOM load");
                        }));

        assertTrue(exception.getMessage().contains("Imported BOM com.example:imported-bom"));
        assertTrue(exception.getMessage().contains("is missing a version"));
        assertTrue(exception.getMessage().contains("Add a version before resolving"));
    }

    private static EffectiveRawPom pom(
            String groupId,
            String artifactId,
            String version,
            Map<String, String> properties,
            List<RawPomDependency> dependencyManagement) {
        RawPom rawPom = new RawPom(
                Optional.of(groupId),
                artifactId,
                Optional.of(version),
                "pom",
                Optional.empty(),
                Optional.empty(),
                properties,
                dependencyManagement,
                List.of());
        return new EffectiveRawPom(
                rawPom,
                List.of(),
                groupId,
                version,
                properties,
                dependencyManagement);
    }

    private static RawPomDependency dependency(
            String groupId,
            String artifactId,
            String version,
            Optional<String> scope,
            Optional<String> type,
            Optional<String> classifier) {
        return new RawPomDependency(
                groupId,
                artifactId,
                Optional.ofNullable(version),
                scope,
                type,
                classifier,
                false,
                List.of());
    }
}
