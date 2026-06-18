package com.zolt.explain;

import com.zolt.project.VersionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GradleMigrationSignalDetector {
    List<ExplainSignal> signals(
            String project,
            String content,
            List<GradleDependencyInspection> dependencies,
            List<GradlePluginInspection> plugins) {
        List<ExplainSignal> signals = new ArrayList<>();
        signals.addAll(conventionPluginSignals(project, plugins));
        signals.addAll(unsupportedPluginSignals(project, content, plugins));
        signals.addAll(dynamicSignals(project, content));
        signals.addAll(dependencySignals(project, dependencies));
        signals.addAll(pluginSignals(project, plugins));
        signals.addAll(enterpriseSignals(project, content));
        return signals;
    }

    private static List<ExplainSignal> conventionPluginSignals(String project, List<GradlePluginInspection> plugins) {
        List<ExplainSignal> signals = new ArrayList<>();
        for (GradlePluginInspection plugin : plugins) {
            if (isConventionPlugin(plugin.id())) {
                signals.add(ExplainSignals.GRADLE_PLUGIN_CONVENTION.signal(
                        project,
                        "Plugin `" + plugin.id() + "` looks like a convention plugin."));
            }
        }
        return signals;
    }

    private static List<ExplainSignal> unsupportedPluginSignals(
            String project,
            String content,
            List<GradlePluginInspection> plugins) {
        List<ExplainSignal> signals = new ArrayList<>();
        for (GradlePluginInspection plugin : plugins) {
            String id = plugin.id().toLowerCase();
            if (id.startsWith("com.android.") || id.equals("android")) {
                signals.add(ExplainSignals.GRADLE_ANDROID_UNSUPPORTED.signal(
                        project,
                        "Gradle plugin `" + plugin.id() + "` declares an Android project, which is outside the Zolt public beta."));
            }
            if (id.startsWith("org.jetbrains.kotlin") || id.equals("kotlin") || id.equals("scala")) {
                signals.add(ExplainSignals.GRADLE_LANGUAGE_UNSUPPORTED.signal(
                        project,
                        "Gradle plugin `" + plugin.id() + "` declares an unsupported public-beta language."));
            }
            if (id.equals("org.graalvm.buildtools.native") || id.equals("io.micronaut.aot")) {
                signals.add(ExplainSignals.GRADLE_FRAMEWORK_NATIVE_UNSUPPORTED.signal(
                        project,
                        "Gradle plugin `" + plugin.id() + "` declares native/AOT behavior that Zolt does not execute in the public beta."));
            }
        }
        if (containsAny(content, "bootBuildImage", "processAot", "processTestAot", "nativeCompile", "nativeTest", "quarkusDev")
                || content.contains("quarkus.native.enabled")) {
            signals.add(ExplainSignals.GRADLE_FRAMEWORK_NATIVE_UNSUPPORTED.signal(
                    project,
                    "Gradle build declares framework AOT, native, or dev-mode tasks that Zolt does not execute in the public beta."));
        }
        return signals;
    }

    private static List<ExplainSignal> dynamicSignals(String project, String content) {
        List<ExplainSignal> signals = new ArrayList<>();
        if (containsAny(content, "dependencies.add(", "configurations.all", "resolutionStrategy", "afterEvaluate")) {
            signals.add(ExplainSignals.GRADLE_IMPERATIVE_DEPENDENCY_LOGIC.signal(
                    project,
                    "Gradle build uses imperative dependency or configuration mutation."));
        }
        if (Pattern.compile("\\b(subprojects|allprojects)\\s*\\{").matcher(content).find()) {
            signals.add(ExplainSignals.GRADLE_CROSS_PROJECT_BUILD_LOGIC.signal(
                    project,
                    "Gradle build uses cross-project script logic."));
        }
        if (Pattern.compile("\\btasks\\.(register|create)\\s*\\(").matcher(content).find()) {
            signals.add(ExplainSignals.GRADLE_CUSTOM_TASK_DETECTED.signal(
                    project,
                    "Gradle build declares custom tasks."));
        }
        return signals;
    }

    private static List<ExplainSignal> dependencySignals(
            String project,
            List<GradleDependencyInspection> dependencies) {
        List<ExplainSignal> signals = new ArrayList<>();
        for (GradleDependencyInspection dependency : dependencies) {
            String version = coordinateVersion(dependency.resolvedCoordinate());
            Optional<VersionPolicy.Violation> violation = unsupportedExternalVersion(version);
            if (violation.isPresent()) {
                signals.add(ExplainSignals.GRADLE_DEPENDENCY_DYNAMIC_VERSION.signal(
                        project,
                        "Dependency `"
                                + dependency.resolvedCoordinate()
                                + "` uses dynamic version `"
                                + version
                                + "` (version-policy rule: "
                                + violation.orElseThrow().rule()
                                + ")."));
            }
        }
        return signals;
    }

    private static List<ExplainSignal> pluginSignals(String project, List<GradlePluginInspection> plugins) {
        List<ExplainSignal> signals = new ArrayList<>();
        boolean enterpriseContext = plugins.stream()
                .map(GradlePluginInspection::id)
                .anyMatch(GradleMigrationSignalDetector::enterprisePluginContext);
        if (!enterpriseContext) {
            return signals;
        }
        for (GradlePluginInspection plugin : plugins) {
            String mapping = enterprisePluginMapping(plugin.id());
            if (!mapping.isBlank()) {
                signals.add(ExplainSignals.GRADLE_ENTERPRISE_PLUGIN_MAPPED.signal(
                        project,
                        "Gradle plugin `" + plugin.id() + "` maps to " + mapping + "."));
            }
        }
        return signals;
    }

    private static String enterprisePluginMapping(String pluginId) {
        return switch (pluginId) {
            case "java", "java-library" -> "Zolt Java source, javac, classpath, and package primitives";
            case "war" -> "Zolt WAR and Spring Boot WAR package modes";
            case "org.springframework.boot" -> "Zolt Spring Boot platform, run, test, and executable archive support";
            case "io.spring.dependency-management" -> "Zolt [platforms] BOM imports and dependency policy";
            case "org.openapi.generator" -> "Zolt typed OpenAPI generated-source steps";
            case "jacoco" -> "Zolt coverage command";
            case "maven-publish" -> "planned Zolt publication metadata, dry-run, and publish commands";
            default -> "";
        };
    }

    private static boolean enterprisePluginContext(String pluginId) {
        return switch (pluginId) {
            case "war",
                    "org.springframework.boot",
                    "io.spring.dependency-management",
                    "org.openapi.generator",
                    "jacoco",
                    "maven-publish" -> true;
            default -> false;
        };
    }

    private static List<ExplainSignal> enterpriseSignals(String project, String content) {
        List<ExplainSignal> signals = new ArrayList<>();
        String repositoriesBlock = block(content, "repositories").orElse("");
        if (repositoriesBlock.contains("credentials")) {
            signals.add(ExplainSignals.GRADLE_REPOSITORY_CREDENTIALS.signal(
                    project,
                    "Gradle repository credentials are resolved inside the build script."));
        }
        if (repositoriesBlock.contains("mavenLocal()")) {
            signals.add(ExplainSignals.GRADLE_REPOSITORY_MAVEN_LOCAL.signal(
                    project,
                    "Gradle build can read Maven-local artifacts through mavenLocal()."));
        }
        if (containsAny(content, "resolutionStrategy", ".force ", " force '", " force \"", "exclude group:")) {
            signals.add(ExplainSignals.GRADLE_DEPENDENCY_POLICY_MUTATION.signal(
                    project,
                    "Gradle build mutates dependency policy through excludes, resolutionStrategy, or forced versions."));
        }
        if (containsAny(content, "GenerateTask", "generatorName", "inputSpec", "sourceSets") && content.contains("openapi")) {
            signals.add(ExplainSignals.GRADLE_OPENAPI_GENERATED_SOURCES.signal(
                    project,
                    "Gradle OpenAPI generator tasks feed generated Java sources into sourceSets."));
        }
        if (containsAny(content, "processResources", "ReplaceTokens", "filter(")) {
            signals.add(ExplainSignals.GRADLE_RESOURCE_FILTERING.signal(
                    project,
                    "Gradle processResources performs token/resource filtering."));
        }
        if (containsAny(content, "tasks.named('test')", "tasks.named(\"test\")", "test {")
                && containsAny(content, "systemProperty", "environment", "jvmArgs", "testLogging")) {
            signals.add(ExplainSignals.GRADLE_TEST_RUNTIME_SETTINGS.signal(
                    project,
                    "Gradle test task declares runtime properties, environment, JVM args, or event logging."));
        }
        if (containsAny(content, "tasks.named('bootWar')", "tasks.named(\"bootWar\")", "bootWar {")
                && containsAny(content, "exclude(", "WEB-INF/lib")) {
            signals.add(ExplainSignals.GRADLE_PACKAGE_ARCHIVE_MUTATION.signal(
                    project,
                    "Gradle bootWar package content is changed with archive excludes."));
        }
        if (containsAny(content, "publishing", "MavenPublication", "publications {", "repositories {")
                && content.contains("mavenJava")) {
            signals.add(ExplainSignals.GRADLE_PUBLICATION_DETECTED.signal(
                    project,
                    "Gradle Maven Publish configuration selects artifacts and repositories."));
        }
        return signals;
    }

    private static Optional<String> block(String content, String name) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\{").matcher(content);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int openBrace = content.indexOf('{', matcher.start());
        int depth = 0;
        for (int index = openBrace; index < content.length(); index++) {
            char character = content.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return Optional.of(content.substring(openBrace + 1, index));
                }
            }
        }
        return Optional.of(content.substring(openBrace + 1));
    }

    private static boolean isConventionPlugin(String id) {
        String lower = id.toLowerCase();
        return lower.contains("convention") || lower.contains("build-logic");
    }

    private static boolean containsAny(String content, String... values) {
        for (String value : values) {
            if (content.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static String coordinateVersion(String coordinate) {
        int lastColon = coordinate.lastIndexOf(':');
        if (lastColon < 0 || lastColon == coordinate.length() - 1) {
            return "";
        }
        return coordinate.substring(lastColon + 1);
    }

    private static Optional<VersionPolicy.Violation> unsupportedExternalVersion(String version) {
        if (version == null || version.isBlank()) {
            return Optional.empty();
        }
        return VersionPolicy.violation(VersionPolicy.Context.EXTERNAL_DEPENDENCY, version);
    }
}
