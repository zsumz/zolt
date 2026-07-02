package sh.zolt.resolve.metadata.pom;

import sh.zolt.maven.Coordinate;
import sh.zolt.maven.repository.EffectiveRawPom;
import sh.zolt.maven.repository.PomInterpolationException;
import sh.zolt.maven.repository.PomPropertyInterpolator;
import sh.zolt.maven.repository.RawPomDependency;
import sh.zolt.resolve.ResolveException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

public final class ImportedBomDependencyManagementExpander {
    private final PomPropertyInterpolator interpolator = new PomPropertyInterpolator();

    List<RawPomDependency> expand(
            EffectiveRawPom pom,
            List<String> importStack,
            BiFunction<Coordinate, List<String>, EffectiveRawPom> importedPomLoader) {
        List<RawPomDependency> dependencies = new ArrayList<>();
        for (RawPomDependency dependency : pom.dependencyManagement()) {
            if (dependency.classifier().isPresent()) {
                dependencies.add(dependency);
                continue;
            }
            if (isImportedBom(dependency)) {
                RawPomDependency interpolated = interpolator.interpolateDependency(dependency, pom);
                Coordinate bomCoordinate = new Coordinate(
                        interpolated.groupId(),
                        interpolated.artifactId(),
                        Optional.of(interpolated.version().orElseThrow(() -> new ResolveException(
                                "Imported BOM "
                                        + interpolated.groupId()
                                        + ":"
                                        + interpolated.artifactId()
                                        + " in "
                                        + pom.groupId()
                                        + ":"
                                        + pom.rawPom().artifactId()
                                        + " is missing a version. Add a version before resolving."))));
                EffectiveRawPom imported = importedPomLoader.apply(bomCoordinate, importStack);
                for (RawPomDependency importedDependency : imported.dependencyManagement()) {
                    if (importedDependency.classifier().isPresent()) {
                        dependencies.add(importedDependency);
                        continue;
                    }
                    interpolateImportedManagedDependency(importedDependency, imported)
                            .ifPresent(dependencies::add);
                }
            } else {
                dependencies.add(dependency);
            }
        }
        return dependencies;
    }

    private static boolean isImportedBom(RawPomDependency dependency) {
        return dependency.type().filter("pom"::equals).isPresent()
                && dependency.scope().filter("import"::equals).isPresent();
    }

    private Optional<RawPomDependency> interpolateImportedManagedDependency(
            RawPomDependency dependency,
            EffectiveRawPom imported) {
        try {
            return Optional.of(interpolator.interpolateDependency(dependency, imported));
        } catch (PomInterpolationException exception) {
            if (dependency.scope().map(scope -> scope.equals("test") || scope.equals("provided")).orElse(false)) {
                return Optional.empty();
            }
            throw exception;
        }
    }
}
