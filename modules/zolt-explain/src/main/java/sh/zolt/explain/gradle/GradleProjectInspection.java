package sh.zolt.explain.gradle;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record GradleProjectInspection(
        Path path,
        String name,
        String buildFile,
        String dsl,
        String javaVersion,
        Optional<String> group,
        Optional<String> version,
        Optional<String> mainClass,
        List<GradlePluginInspection> plugins,
        List<GradleRepositoryInspection> repositories,
        List<GradleDependencyInspection> dependencies,
        List<String> sourceRoots,
        List<String> testSourceRoots,
        List<String> groovyTestSourceRoots) {
    public GradleProjectInspection {
        group = group == null ? Optional.empty() : group;
        version = version == null ? Optional.empty() : version;
        mainClass = mainClass == null ? Optional.empty() : mainClass;
        plugins = List.copyOf(plugins);
        repositories = List.copyOf(repositories);
        dependencies = List.copyOf(dependencies);
        sourceRoots = List.copyOf(sourceRoots);
        testSourceRoots = List.copyOf(testSourceRoots);
        groovyTestSourceRoots = List.copyOf(groovyTestSourceRoots);
    }
}
