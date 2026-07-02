package sh.zolt.toml.dependency;

import sh.zolt.toml.ZoltConfigException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class DependencySectionDuplicateValidator {
    private DependencySectionDuplicateValidator() {
    }

    static void validateNoDuplicateMainDependencyCoordinates(
            DependencySectionCodec.DependencyDeclarations apiDependencies,
            DependencySectionCodec.DependencyDeclarations implementationDependencies,
            DependencySectionCodec.DependencyDeclarations runtimeDependencies,
            DependencySectionCodec.DependencyDeclarations providedDependencies,
            DependencySectionCodec.DependencyDeclarations devDependencies) {
        Map<String, String> sections = new LinkedHashMap<>();
        addCoordinates(sections, apiDependencies, "api.dependencies");
        addCoordinates(sections, implementationDependencies, "dependencies");
        addCoordinates(sections, runtimeDependencies, "runtime.dependencies");
        addCoordinates(sections, providedDependencies, "provided.dependencies");
        addCoordinates(sections, devDependencies, "dev.dependencies");
    }

    private static void addCoordinates(
            Map<String, String> sections,
            DependencySectionCodec.DependencyDeclarations declarations,
            String section) {
        for (String coordinate : allCoordinates(declarations)) {
            String existing = sections.putIfAbsent(coordinate, section);
            if (existing != null) {
                throw new ZoltConfigException(
                        "Dependency "
                                + coordinate
                                + " is declared in both ["
                                + existing
                                + "] and ["
                                + section
                                + "]. Keep it in one section.");
            }
        }
    }

    private static Set<String> allCoordinates(DependencySectionCodec.DependencyDeclarations declarations) {
        LinkedHashSet<String> coordinates = new LinkedHashSet<>();
        coordinates.addAll(declarations.versioned().keySet());
        coordinates.addAll(declarations.managed());
        coordinates.addAll(declarations.workspace().keySet());
        return Set.copyOf(coordinates);
    }
}
