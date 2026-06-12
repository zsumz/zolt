package com.zolt.explain;

import com.zolt.project.VersionPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public final class MavenStaticProjectInspector {
    public MavenInspectionResult inspect(Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path rootPom = normalizedRoot.resolve("pom.xml");
        if (!Files.exists(rootPom)) {
            throw new MigrationExplainException(
                    "Could not inspect Maven project. Expected pom.xml at " + rootPom
                            + ". Run zolt explain from a Maven project root or pass --cwd.");
        }
        if (!Files.isRegularFile(rootPom)) {
            throw new MigrationExplainException(
                    "Could not inspect Maven project. Expected pom.xml to be a regular file: " + rootPom);
        }

        List<MavenProjectInspection> projects = new ArrayList<>();
        List<ExplainSignal> signals = new ArrayList<>();
        inspectPom(normalizedRoot, normalizedRoot, projects, signals);
        projects.sort(Comparator.comparing(project -> project.path().toString()));
        return new MavenInspectionResult(normalizedRoot, projects, ExplainSignals.sorted(signals));
    }

    private void inspectPom(
            Path root,
            Path projectDirectory,
            List<MavenProjectInspection> projects,
            List<ExplainSignal> signals) {
        Path pom = projectDirectory.resolve("pom.xml");
        Document document = document(pom);
        Element project = document.getDocumentElement();
        if (project == null || !hasName(project, "project")) {
            throw new MigrationExplainException(
                    "Could not inspect Maven project. Expected root <project> element in " + pom + ".");
        }

        Path relativePath = relativePath(root, projectDirectory);
        String projectLabel = relativePath.toString().isBlank() ? "." : relativePath.toString();
        Map<String, String> properties = parseProperties(project);
        List<String> modules = texts(child(project, "modules"), "module");
        List<MavenDependencyInspection> dependencies = parseDependencies(child(project, "dependencies"), false);
        List<MavenDependencyInspection> dependencyManagement = child(project, "dependencyManagement")
                .flatMap(element -> child(element, "dependencies"))
                .map(element -> parseDependencies(Optional.of(element), true))
                .orElseGet(List::of);
        List<MavenDependencyInspection> importedBoms = dependencyManagement.stream()
                .filter(MavenDependencyInspection::importedBom)
                .toList();
        List<MavenRepositoryInspection> repositories = repositories(project);
        List<MavenPluginInspection> plugins = plugins(project);
        List<MavenProfileInspection> profiles = profiles(project);

        MavenProjectInspection inspection = new MavenProjectInspection(
                relativePath,
                text(project, "artifactId").orElse(projectDirectory.getFileName().toString()),
                text(project, "packaging").orElse("jar"),
                javaVersion(project, properties, plugins),
                modules,
                sourceRoots(project, "sourceDirectory", "src/main/java"),
                sourceRoots(project, "testSourceDirectory", "src/test/java"),
                resourceRoots(project),
                dependencies,
                dependencyManagement,
                importedBoms,
                repositories,
                plugins,
                profiles);
        projects.add(inspection);
        signals.addAll(signalsFor(projectLabel, inspection));

        for (String module : modules) {
            Path moduleDirectory = projectDirectory.resolve(module).normalize();
            Path modulePom = moduleDirectory.resolve("pom.xml");
            if (Files.isRegularFile(modulePom)) {
                inspectPom(root, moduleDirectory, projects, signals);
            } else {
                signals.add(ExplainSignals.MAVEN_MODULE_MISSING_POM.signal(
                        projectLabel,
                        "Module `" + module + "` does not contain a readable pom.xml."));
            }
        }
    }

    private Document document(Path pom) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new QuietErrorHandler());
            try (InputStream inputStream = Files.newInputStream(pom)) {
                return builder.parse(inputStream);
            }
        } catch (ParserConfigurationException exception) {
            throw new MigrationExplainException("Could not configure secure Maven POM XML parser.", exception);
        } catch (SAXException exception) {
            throw new MigrationExplainException(
                    "Could not inspect Maven project. Fix malformed POM XML before running zolt explain: " + pom,
                    exception);
        } catch (IOException exception) {
            throw new MigrationExplainException("Could not read Maven POM for zolt explain: " + pom, exception);
        }
    }

    private List<ExplainSignal> signalsFor(String project, MavenProjectInspection inspection) {
        List<ExplainSignal> signals = new ArrayList<>();
        if (unsupportedPackaging(inspection.packaging())) {
            signals.add(ExplainSignals.MAVEN_PACKAGING_UNSUPPORTED.signal(
                    project,
                    "Packaging `" + inspection.packaging() + "` needs an explicit Zolt packaging primitive."));
        }
        for (MavenDependencyInspection dependency : concat(inspection.dependencies(), inspection.dependencyManagement())) {
            Optional<VersionPolicy.Violation> violation = unsupportedExternalVersion(dependency.version());
            if (violation.isPresent()) {
                signals.add(ExplainSignals.MAVEN_DEPENDENCY_DYNAMIC_VERSION.signal(
                        project,
                        "Dependency `"
                                + dependency.coordinate()
                                + "` uses dynamic version `"
                                + dependency.version()
                                + "` (version-policy rule: "
                                + violation.orElseThrow().rule()
                                + ")."));
            }
        }
        for (MavenPluginInspection plugin : inspection.plugins()) {
            if (!plugin.phases().isEmpty() && !plugin.pluginManagement()) {
                signals.add(ExplainSignals.MAVEN_PLUGIN_LIFECYCLE_BINDING.signal(
                        project,
                        "Plugin `" + plugin.coordinate() + "` runs in lifecycle phase(s) " + plugin.phases() + "."));
            } else if (!knownPlugin(plugin.coordinate())) {
                signals.add(ExplainSignals.MAVEN_PLUGIN_STATIC_SIGNAL.signal(
                        project,
                        "Plugin `" + plugin.coordinate() + "` is declared and was not executed."));
            }
        }
        for (MavenProfileInspection profile : inspection.profiles()) {
            String activation = profile.activationHints().isEmpty()
                    ? "manual activation"
                    : String.join(", ", profile.activationHints());
            signals.add(ExplainSignals.MAVEN_PROFILE_DETECTED.signal(
                    project,
                    "Profile `" + profile.id() + "` is present with " + activation + "."));
        }
        return signals;
    }

    private static List<MavenDependencyInspection> parseDependencies(
            Optional<Element> dependenciesElement,
            boolean managed) {
        if (dependenciesElement.isEmpty()) {
            return List.of();
        }
        List<MavenDependencyInspection> dependencies = new ArrayList<>();
        for (Element dependency : children(dependenciesElement.orElseThrow(), "dependency")) {
            String groupId = text(dependency, "groupId").orElse("unknown-group");
            String artifactId = text(dependency, "artifactId").orElse("unknown-artifact");
            String version = text(dependency, "version").orElse("");
            String scope = text(dependency, "scope").orElse("compile");
            String type = text(dependency, "type").orElse("jar");
            boolean optional = text(dependency, "optional").map(Boolean::parseBoolean).orElse(false);
            boolean importedBom = managed && "pom".equals(type) && "import".equals(scope);
            dependencies.add(new MavenDependencyInspection(
                    scope,
                    groupId + ":" + artifactId + (version.isBlank() ? "" : ":" + version),
                    version,
                    type,
                    optional,
                    managed,
                    importedBom));
        }
        dependencies.sort(Comparator
                .comparing(MavenDependencyInspection::scope)
                .thenComparing(MavenDependencyInspection::coordinate));
        return dependencies;
    }

    private static List<MavenRepositoryInspection> repositories(Element project) {
        List<MavenRepositoryInspection> repositories = new ArrayList<>();
        repositories.addAll(repositoryList(child(project, "repositories"), false));
        repositories.addAll(repositoryList(child(project, "pluginRepositories"), true));
        repositories.sort(Comparator
                .comparing(MavenRepositoryInspection::pluginRepository)
                .thenComparing(MavenRepositoryInspection::id)
                .thenComparing(MavenRepositoryInspection::url));
        return repositories;
    }

    private static List<MavenRepositoryInspection> repositoryList(
            Optional<Element> repositoriesElement,
            boolean pluginRepository) {
        if (repositoriesElement.isEmpty()) {
            return List.of();
        }
        List<MavenRepositoryInspection> repositories = new ArrayList<>();
        for (Element repository : children(repositoriesElement.orElseThrow(), "repository")) {
            repositories.add(new MavenRepositoryInspection(
                    text(repository, "id").orElse("unknown"),
                    text(repository, "url").orElse(""),
                    pluginRepository));
        }
        return repositories;
    }

    private static List<MavenPluginInspection> plugins(Element project) {
        Optional<Element> build = child(project, "build");
        if (build.isEmpty()) {
            return List.of();
        }
        List<MavenPluginInspection> plugins = new ArrayList<>();
        plugins.addAll(pluginList(child(build.orElseThrow(), "plugins"), false));
        child(build.orElseThrow(), "pluginManagement")
                .flatMap(element -> child(element, "plugins"))
                .ifPresent(element -> plugins.addAll(pluginList(Optional.of(element), true)));
        plugins.sort(Comparator
                .comparing(MavenPluginInspection::coordinate)
                .thenComparing(MavenPluginInspection::pluginManagement));
        return plugins;
    }

    private static List<MavenPluginInspection> pluginList(Optional<Element> pluginsElement, boolean pluginManagement) {
        if (pluginsElement.isEmpty()) {
            return List.of();
        }
        List<MavenPluginInspection> plugins = new ArrayList<>();
        for (Element plugin : children(pluginsElement.orElseThrow(), "plugin")) {
            String groupId = text(plugin, "groupId").orElse("org.apache.maven.plugins");
            String artifactId = text(plugin, "artifactId").orElse("unknown-plugin");
            String version = text(plugin, "version").orElse("");
            List<String> phases = child(plugin, "executions")
                    .map(executions -> children(executions, "execution").stream()
                            .map(execution -> text(execution, "phase"))
                            .flatMap(Optional::stream)
                            .sorted()
                            .toList())
                    .orElseGet(List::of);
            plugins.add(new MavenPluginInspection(
                    groupId + ":" + artifactId + (version.isBlank() ? "" : ":" + version),
                    phases,
                    pluginManagement));
        }
        return plugins;
    }

    private static List<MavenProfileInspection> profiles(Element project) {
        Optional<Element> profilesElement = child(project, "profiles");
        if (profilesElement.isEmpty()) {
            return List.of();
        }
        List<MavenProfileInspection> profiles = new ArrayList<>();
        for (Element profile : children(profilesElement.orElseThrow(), "profile")) {
            profiles.add(new MavenProfileInspection(
                    text(profile, "id").orElse("unknown"),
                    activationHints(profile)));
        }
        profiles.sort(Comparator.comparing(MavenProfileInspection::id));
        return profiles;
    }

    private static List<String> activationHints(Element profile) {
        Optional<Element> activation = child(profile, "activation");
        if (activation.isEmpty()) {
            return List.of();
        }
        List<String> hints = new ArrayList<>();
        text(activation.orElseThrow(), "jdk").ifPresent(value -> hints.add("jdk " + value));
        child(activation.orElseThrow(), "property").ifPresent(property -> {
            text(property, "name").ifPresent(value -> hints.add("property " + value));
            text(property, "value").ifPresent(value -> hints.add("property value " + value));
        });
        child(activation.orElseThrow(), "os").ifPresent(ignored -> hints.add("operating system"));
        child(activation.orElseThrow(), "file").ifPresent(ignored -> hints.add("file presence"));
        if (hints.isEmpty()) {
            hints.add("activation block");
        }
        hints.sort(String::compareTo);
        return hints;
    }

    private static String javaVersion(
            Element project,
            Map<String, String> properties,
            List<MavenPluginInspection> plugins) {
        for (String key : List.of("maven.compiler.release", "maven.compiler.target", "maven.compiler.source", "java.version")) {
            String value = properties.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        Optional<Element> build = child(project, "build");
        if (build.isEmpty()) {
            return "unknown";
        }
        Optional<Element> compilerPlugin = child(build.orElseThrow(), "plugins").stream()
                .flatMap(pluginsElement -> children(pluginsElement, "plugin").stream())
                .filter(plugin -> "maven-compiler-plugin".equals(text(plugin, "artifactId").orElse("")))
                .findFirst();
        if (compilerPlugin.isEmpty()) {
            return "unknown";
        }
        Optional<Element> configuration = child(compilerPlugin.orElseThrow(), "configuration");
        if (configuration.isEmpty()) {
            return "unknown";
        }
        for (String key : List.of("release", "target", "source")) {
            Optional<String> value = text(configuration.orElseThrow(), key);
            if (value.isPresent()) {
                return value.orElseThrow();
            }
        }
        return "unknown";
    }

    private static List<String> sourceRoots(Element project, String elementName, String defaultRoot) {
        Optional<Element> build = child(project, "build");
        return build.flatMap(element -> text(element, elementName))
                .map(List::of)
                .orElseGet(() -> List.of(defaultRoot));
    }

    private static List<String> resourceRoots(Element project) {
        Optional<Element> build = child(project, "build");
        if (build.isEmpty()) {
            return List.of("src/main/resources");
        }
        Optional<Element> resources = child(build.orElseThrow(), "resources");
        if (resources.isEmpty()) {
            return List.of("src/main/resources");
        }
        List<String> roots = new ArrayList<>();
        for (Element resource : children(resources.orElseThrow(), "resource")) {
            text(resource, "directory").ifPresent(roots::add);
        }
        return roots.isEmpty() ? List.of("src/main/resources") : List.copyOf(roots);
    }

    private static Map<String, String> parseProperties(Element project) {
        Optional<Element> properties = child(project, "properties");
        if (properties.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Element property : children(properties.orElseThrow())) {
            values.put(name(property), property.getTextContent().trim());
        }
        return values;
    }

    private static Optional<Element> child(Element parent, String childName) {
        for (Element child : children(parent)) {
            if (hasName(child, childName)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    private static List<String> texts(Optional<Element> parent, String childName) {
        if (parent.isEmpty()) {
            return List.of();
        }
        return children(parent.orElseThrow(), childName).stream()
                .map(Element::getTextContent)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static Optional<String> text(Element parent, String childName) {
        return child(parent, childName)
                .map(Element::getTextContent)
                .map(String::trim)
                .filter(value -> !value.isBlank());
    }

    private static List<Element> children(Element parent, String childName) {
        return children(parent).stream()
                .filter(child -> hasName(child, childName))
                .toList();
    }

    private static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        for (int index = 0; index < parent.getChildNodes().getLength(); index++) {
            Node node = parent.getChildNodes().item(index);
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static boolean hasName(Element element, String expected) {
        return expected.equals(name(element));
    }

    private static String name(Element element) {
        String localName = element.getLocalName();
        return localName == null ? element.getNodeName() : localName;
    }

    private static Path relativePath(Path root, Path projectDirectory) {
        Path relative = root.relativize(projectDirectory);
        return relative.toString().isBlank() ? Path.of(".") : relative;
    }

    private static boolean unsupportedPackaging(String packaging) {
        return !Set.of("jar", "pom").contains(packaging);
    }

    private static Optional<VersionPolicy.Violation> unsupportedExternalVersion(String version) {
        if (version == null || version.isBlank()) {
            return Optional.empty();
        }
        return VersionPolicy.violation(VersionPolicy.Context.EXTERNAL_DEPENDENCY, version);
    }

    private static boolean knownPlugin(String coordinate) {
        return coordinate.contains(":maven-compiler-plugin")
                || coordinate.contains(":maven-surefire-plugin")
                || coordinate.contains(":maven-failsafe-plugin")
                || coordinate.contains(":spring-boot-maven-plugin");
    }

    private static List<MavenDependencyInspection> concat(
            List<MavenDependencyInspection> first,
            List<MavenDependencyInspection> second) {
        List<MavenDependencyInspection> combined = new ArrayList<>(first);
        combined.addAll(second);
        return combined;
    }

    private static final class QuietErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}
