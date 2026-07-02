package sh.zolt.explain.maven;

import static sh.zolt.explain.maven.MavenXml.child;
import static sh.zolt.explain.maven.MavenXml.children;
import static sh.zolt.explain.maven.MavenXml.text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.w3c.dom.Element;

final class MavenAnnotationProcessorPaths {
    private MavenAnnotationProcessorPaths() {
    }

    static List<MavenAnnotationProcessorInspection> parse(Element project, MavenPomProperties properties) {
        List<MavenAnnotationProcessorInspection> processors = new ArrayList<>();
        for (Element plugin : compilerPlugins(project)) {
            child(plugin, "configuration")
                    .flatMap(configuration -> child(configuration, "annotationProcessorPaths"))
                    .ifPresent(paths -> processors.addAll(annotationProcessorPaths(paths, properties)));
        }
        processors.sort(Comparator.comparing(MavenAnnotationProcessorInspection::coordinate));
        return processors;
    }

    private static List<Element> compilerPlugins(Element project) {
        Optional<Element> build = child(project, "build");
        if (build.isEmpty()) {
            return List.of();
        }
        List<Element> plugins = new ArrayList<>();
        plugins.addAll(compilerPlugins(child(build.orElseThrow(), "plugins")));
        child(build.orElseThrow(), "pluginManagement")
                .flatMap(element -> child(element, "plugins"))
                .ifPresent(element -> plugins.addAll(compilerPlugins(Optional.of(element))));
        return plugins;
    }

    private static List<Element> compilerPlugins(Optional<Element> pluginsElement) {
        if (pluginsElement.isEmpty()) {
            return List.of();
        }
        return children(pluginsElement.orElseThrow(), "plugin").stream()
                .filter(MavenAnnotationProcessorPaths::compilerPlugin)
                .toList();
    }

    private static boolean compilerPlugin(Element plugin) {
        String artifactId = text(plugin, "artifactId").orElse("");
        String groupId = text(plugin, "groupId").orElse("org.apache.maven.plugins");
        return "maven-compiler-plugin".equals(artifactId)
                && ("org.apache.maven.plugins".equals(groupId) || groupId.isBlank());
    }

    private static List<MavenAnnotationProcessorInspection> annotationProcessorPaths(
            Element paths,
            MavenPomProperties properties) {
        List<MavenAnnotationProcessorInspection> processors = new ArrayList<>();
        for (Element path : children(paths, "path")) {
            String groupId = properties.interpolate(text(path, "groupId").orElse("unknown-group"));
            String artifactId = properties.interpolate(text(path, "artifactId").orElse("unknown-artifact"));
            String version = properties.interpolate(text(path, "version").orElse(""));
            processors.add(new MavenAnnotationProcessorInspection(
                    groupId + ":" + artifactId + (version.isBlank() ? "" : ":" + version),
                    version));
        }
        return processors;
    }
}
