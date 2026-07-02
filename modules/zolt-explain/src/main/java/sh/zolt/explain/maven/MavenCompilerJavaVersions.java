package sh.zolt.explain.maven;

import static sh.zolt.explain.maven.MavenXml.child;
import static sh.zolt.explain.maven.MavenXml.children;
import static sh.zolt.explain.maven.MavenXml.text;

import java.util.List;
import java.util.Optional;
import org.w3c.dom.Element;

record MavenCompilerJavaVersions(String mainVersion, String testVersion) {
    static MavenCompilerJavaVersions inspect(Element project, MavenPomProperties properties) {
        Optional<Element> configuration = compilerPluginConfiguration(project);
        String mainVersion = javaVersion(properties, configuration);
        String testVersion = testJavaVersion(properties, configuration);
        if (testVersion.equals(mainVersion)) {
            testVersion = "";
        }
        return new MavenCompilerJavaVersions(
                mainVersion,
                testVersion);
    }

    private static String javaVersion(MavenPomProperties properties, Optional<Element> configuration) {
        Optional<String> propertyVersion = compilerProperty(properties, List.of(
                "maven.compiler.release",
                "maven.compiler.target",
                "maven.compiler.source",
                "java.version"));
        if (propertyVersion.isPresent()) {
            return propertyVersion.orElseThrow();
        }
        if (configuration.isPresent()) {
            for (String key : List.of("release", "target", "source")) {
                Optional<String> value = text(configuration.orElseThrow(), key);
                if (value.isPresent()) {
                    return properties.interpolate(value.orElseThrow());
                }
            }
        }
        return "unknown";
    }

    private static String testJavaVersion(MavenPomProperties properties, Optional<Element> configuration) {
        Optional<String> propertyVersion = compilerProperty(properties, List.of(
                "maven.compiler.testRelease",
                "maven.compiler.testTarget",
                "maven.compiler.testSource"));
        if (propertyVersion.isPresent()) {
            return propertyVersion.orElseThrow();
        }
        if (configuration.isPresent()) {
            for (String key : List.of("testRelease", "testTarget", "testSource")) {
                Optional<String> value = text(configuration.orElseThrow(), key);
                if (value.isPresent()) {
                    return properties.interpolate(value.orElseThrow());
                }
            }
        }
        return "";
    }

    private static Optional<String> compilerProperty(MavenPomProperties properties, List<String> keys) {
        for (String key : keys) {
            String value = properties.values().get(key);
            if (value != null && !value.isBlank()) {
                return Optional.of(properties.interpolate(value));
            }
        }
        return Optional.empty();
    }

    private static Optional<Element> compilerPluginConfiguration(Element project) {
        Optional<Element> build = child(project, "build");
        if (build.isEmpty()) {
            return Optional.empty();
        }
        Optional<Element> compilerPlugin = child(build.orElseThrow(), "plugins").stream()
                .flatMap(pluginsElement -> children(pluginsElement, "plugin").stream())
                .filter(plugin -> "maven-compiler-plugin".equals(text(plugin, "artifactId").orElse("")))
                .findFirst();
        if (compilerPlugin.isEmpty()) {
            return Optional.empty();
        }
        return child(compilerPlugin.orElseThrow(), "configuration");
    }
}
