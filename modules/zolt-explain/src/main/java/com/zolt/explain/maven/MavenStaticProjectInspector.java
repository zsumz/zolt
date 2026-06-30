package com.zolt.explain.maven;

import com.zolt.explain.ExplainSignal;
import com.zolt.explain.ExplainSignals;
import com.zolt.explain.MigrationExplainException;
import com.zolt.project.VersionPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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

        Path relativePath = relativePath(root, projectDirectory);
        String projectLabel = relativePath.toString().isBlank() ? "." : relativePath.toString();
        MavenProjectInspection inspection = MavenProjectInspectionBuilder.build(pom, relativePath, projectDirectory, project);
        projects.add(inspection);
        signals.addAll(signalsFor(projectLabel, inspection));

        for (String module : inspection.modules()) {
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
            if (unsupportedLanguagePlugin(plugin.coordinate())) {
                signals.add(ExplainSignals.MAVEN_LANGUAGE_UNSUPPORTED.signal(
                        project,
                        "Plugin `" + plugin.coordinate() + "` declares an unsupported public-beta language or Android build."));
            } else if (unsupportedFrameworkNativePlugin(plugin)) {
                signals.add(ExplainSignals.MAVEN_FRAMEWORK_NATIVE_UNSUPPORTED.signal(
                        project,
                        "Plugin `" + plugin.coordinate() + "` declares framework AOT/native behavior that Zolt does not execute as Maven lifecycle behavior; migrate supported cases to typed Zolt framework settings."));
            } else if (!plugin.phases().isEmpty() && !plugin.pluginManagement()) {
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

    private static boolean unsupportedLanguagePlugin(String coordinate) {
        String lower = coordinate.toLowerCase();
        return lower.contains(":kotlin-maven-plugin")
                || lower.contains(":scala-maven-plugin")
                || lower.contains(":android-maven-plugin");
    }

    private static boolean unsupportedFrameworkNativePlugin(MavenPluginInspection plugin) {
        String lower = plugin.coordinate().toLowerCase();
        if (lower.contains(":native-maven-plugin") || lower.contains(":micronaut-maven-plugin")) {
            return true;
        }
        if (!lower.contains(":spring-boot-maven-plugin")) {
            return false;
        }
        return plugin.goals().stream()
                .map(String::toLowerCase)
                .anyMatch(goal -> goal.contains("aot") || goal.contains("build-image") || goal.contains("native"));
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
