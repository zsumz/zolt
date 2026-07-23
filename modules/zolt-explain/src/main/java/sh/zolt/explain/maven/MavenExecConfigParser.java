package sh.zolt.explain.maven;

import static sh.zolt.explain.maven.MavenXml.child;
import static sh.zolt.explain.maven.MavenXml.children;
import static sh.zolt.explain.maven.MavenXml.name;
import static sh.zolt.explain.maven.MavenXml.text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.w3c.dom.Element;

/**
 * Extracts {@link MavenExecInvocation}s from an exec-shaped Maven plugin element. Only the three
 * plugin families Zolt can draft as exec steps are recognized — {@code exec-maven-plugin} ({@code
 * java}/{@code exec} goals), {@code frontend-maven-plugin}, and {@code maven-antrun-plugin} — and the
 * extraction is purely static: execution {@code <configuration>} overrides plugin-level
 * {@code <configuration>}, and anything Zolt cannot read stays empty on the invocation.
 */
final class MavenExecConfigParser {
    private static final Set<String> EXEC_GOALS = Set.of("java", "exec");
    private static final Set<String> FRONTEND_GOALS = Set.of(
            "npm", "yarn", "pnpm", "bun", "bower", "grunt", "gulp", "webpack", "karma", "ember",
            "install-node-and-npm", "install-node-and-yarn", "install-node-and-pnpm");
    private static final List<String> SHELL_METACHARACTERS = List.of("&&", "||", "|", ";", "$(", "`", ">", "<");

    private MavenExecConfigParser() {
    }

    static boolean isExecShaped(String artifactId) {
        return kind(artifactId) != null;
    }

    static List<MavenExecInvocation> invocations(
            String artifactId,
            Element plugin,
            List<Element> executions,
            MavenPomProperties properties) {
        String kind = kind(artifactId);
        if (kind == null) {
            return List.of();
        }
        Optional<Element> pluginConfig = child(plugin, "configuration");
        List<MavenExecInvocation> invocations = new ArrayList<>();
        for (Element execution : executions) {
            Optional<Element> executionConfig = child(execution, "configuration");
            Optional<String> phase = text(execution, "phase")
                    .map(properties::interpolate)
                    .map(String::strip)
                    .filter(value -> !value.isBlank() && !"none".equalsIgnoreCase(value));
            String executionId = text(execution, "id").map(properties::interpolate).map(String::strip).orElse("");
            for (String goal : executionGoals(execution, properties)) {
                if (!relevant(kind, goal)) {
                    continue;
                }
                invocations.add(build(kind, goal, executionId, phase, executionConfig, pluginConfig, properties));
            }
        }
        return List.copyOf(invocations);
    }

    private static MavenExecInvocation build(
            String kind,
            String goal,
            String executionId,
            Optional<String> phase,
            Optional<Element> executionConfig,
            Optional<Element> pluginConfig,
            MavenPomProperties properties) {
        return switch (kind) {
            case "exec" -> execInvocation(goal, executionId, phase, executionConfig, pluginConfig, properties);
            case "frontend" -> frontendInvocation(goal, executionId, phase, executionConfig, pluginConfig, properties);
            default -> antrunInvocation(executionId, phase, executionConfig, pluginConfig, properties);
        };
    }

    private static MavenExecInvocation execInvocation(
            String goal,
            String executionId,
            Optional<String> phase,
            Optional<Element> executionConfig,
            Optional<Element> pluginConfig,
            MavenPomProperties properties) {
        Optional<String> mainClass = mergedText(executionConfig, pluginConfig, "mainClass", properties);
        Optional<String> executable = mergedText(executionConfig, pluginConfig, "executable", properties);
        List<String> arguments = argumentList(executionConfig, pluginConfig, properties);
        Optional<String> workingDirectory = mergedText(executionConfig, pluginConfig, "workingDirectory", properties)
                .map(MavenExecConfigParser::relativeDirectory);
        Map<String, String> environment = environmentVariables(executionConfig, pluginConfig, properties);
        List<String> executableDependencies = executableDependencies(executionConfig, pluginConfig, properties);
        return new MavenExecInvocation(
                executionId, goal, phase, mainClass, executable, arguments, workingDirectory,
                environment, executableDependencies, anyShellUnsafe(arguments), false);
    }

    private static MavenExecInvocation frontendInvocation(
            String goal,
            String executionId,
            Optional<String> phase,
            Optional<Element> executionConfig,
            Optional<Element> pluginConfig,
            MavenPomProperties properties) {
        Optional<String> workingDirectory = mergedText(executionConfig, pluginConfig, "workingDirectory", properties)
                .map(MavenExecConfigParser::relativeDirectory);
        Map<String, String> environment = environmentVariables(executionConfig, pluginConfig, properties);
        List<String> arguments = frontendArguments(executionConfig, pluginConfig, properties);
        Optional<String> executable = Optional.of(frontendBinary(goal));
        return new MavenExecInvocation(
                executionId, goal, phase, Optional.empty(), executable, arguments, workingDirectory,
                environment, List.of(), anyShellUnsafe(arguments), false);
    }

    private static MavenExecInvocation antrunInvocation(
            String executionId,
            Optional<String> phase,
            Optional<Element> executionConfig,
            Optional<Element> pluginConfig,
            MavenPomProperties properties) {
        Optional<Element> target = mergedChild(executionConfig, pluginConfig, "target")
                .or(() -> mergedChild(executionConfig, pluginConfig, "tasks"));
        if (target.isEmpty()) {
            return unmappableAntrun(executionId, phase);
        }
        List<Element> tasks = children(target.orElseThrow());
        if (tasks.size() != 1 || !"exec".equals(name(tasks.get(0)))) {
            return unmappableAntrun(executionId, phase);
        }
        Element exec = tasks.get(0);
        Optional<String> executable = attribute(exec, "executable", properties);
        List<String> arguments = antrunArguments(exec, properties);
        return new MavenExecInvocation(
                executionId, "run", phase, Optional.empty(), executable, arguments, Optional.empty(),
                Map.of(), List.of(), anyShellUnsafe(arguments), executable.isEmpty());
    }

    private static MavenExecInvocation unmappableAntrun(String executionId, Optional<String> phase) {
        return new MavenExecInvocation(
                executionId, "run", phase, Optional.empty(), Optional.empty(), List.of(), Optional.empty(),
                Map.of(), List.of(), false, true);
    }

    private static List<String> antrunArguments(Element exec, MavenPomProperties properties) {
        List<String> arguments = new ArrayList<>();
        for (Element arg : children(exec, "arg")) {
            attribute(arg, "value", properties).ifPresent(arguments::add);
            attribute(arg, "line", properties)
                    .ifPresent(line -> arguments.addAll(List.of(line.split("\\s+"))));
        }
        return List.copyOf(arguments);
    }

    private static List<String> argumentList(
            Optional<Element> executionConfig,
            Optional<Element> pluginConfig,
            MavenPomProperties properties) {
        Optional<Element> arguments = mergedChild(executionConfig, pluginConfig, "arguments");
        if (arguments.isEmpty()) {
            return List.of();
        }
        List<Element> argumentElements = children(arguments.orElseThrow(), "argument");
        if (argumentElements.isEmpty()) {
            String inline = arguments.orElseThrow().getTextContent().strip();
            return inline.isBlank() ? List.of() : List.of(properties.interpolate(inline).split("\\s+"));
        }
        List<String> values = new ArrayList<>();
        for (Element argument : argumentElements) {
            String value = properties.interpolate(argument.getTextContent().strip());
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static List<String> frontendArguments(
            Optional<Element> executionConfig,
            Optional<Element> pluginConfig,
            MavenPomProperties properties) {
        Optional<String> inline = mergedText(executionConfig, pluginConfig, "arguments", properties);
        if (inline.isEmpty()) {
            return List.of();
        }
        return List.of(inline.orElseThrow().split("\\s+"));
    }

    private static Map<String, String> environmentVariables(
            Optional<Element> executionConfig,
            Optional<Element> pluginConfig,
            MavenPomProperties properties) {
        Optional<Element> environment = mergedChild(executionConfig, pluginConfig, "environmentVariables");
        if (environment.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Element entry : children(environment.orElseThrow())) {
            String value = properties.interpolate(entry.getTextContent().strip());
            values.put(name(entry), value);
        }
        return values;
    }

    private static List<String> executableDependencies(
            Optional<Element> executionConfig,
            Optional<Element> pluginConfig,
            MavenPomProperties properties) {
        Optional<Element> dependencies = mergedChild(executionConfig, pluginConfig, "executableDependencies");
        if (dependencies.isEmpty()) {
            return List.of();
        }
        List<String> coordinates = new ArrayList<>();
        for (Element dependency : children(dependencies.orElseThrow(), "dependency")) {
            Optional<String> groupId = text(dependency, "groupId").map(properties::interpolate);
            Optional<String> artifactId = text(dependency, "artifactId").map(properties::interpolate);
            if (groupId.isPresent() && artifactId.isPresent()) {
                coordinates.add(groupId.orElseThrow() + ":" + artifactId.orElseThrow());
            }
        }
        return List.copyOf(coordinates);
    }

    private static Optional<String> mergedText(
            Optional<Element> executionConfig,
            Optional<Element> pluginConfig,
            String key,
            MavenPomProperties properties) {
        return mergedChild(executionConfig, pluginConfig, key)
                .map(Element::getTextContent)
                .map(String::strip)
                .map(properties::interpolate)
                .filter(value -> !value.isBlank());
    }

    private static Optional<Element> mergedChild(
            Optional<Element> executionConfig,
            Optional<Element> pluginConfig,
            String key) {
        Optional<Element> fromExecution = executionConfig.flatMap(config -> child(config, key));
        if (fromExecution.isPresent()) {
            return fromExecution;
        }
        return pluginConfig.flatMap(config -> child(config, key));
    }

    private static Optional<String> attribute(Element element, String name, MavenPomProperties properties) {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(properties.interpolate(value.strip()));
    }

    private static List<String> executionGoals(Element execution, MavenPomProperties properties) {
        return child(execution, "goals").stream()
                .flatMap(goals -> children(goals, "goal").stream())
                .map(Element::getTextContent)
                .map(String::strip)
                .map(properties::interpolate)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static boolean relevant(String kind, String goal) {
        return switch (kind) {
            case "exec" -> EXEC_GOALS.contains(goal);
            case "frontend" -> FRONTEND_GOALS.contains(goal);
            default -> "run".equals(goal);
        };
    }

    private static String frontendBinary(String goal) {
        return switch (goal) {
            case "install-node-and-npm" -> "npm";
            case "install-node-and-yarn" -> "yarn";
            case "install-node-and-pnpm" -> "pnpm";
            default -> goal;
        };
    }

    private static boolean anyShellUnsafe(List<String> arguments) {
        for (String argument : arguments) {
            for (String metacharacter : SHELL_METACHARACTERS) {
                if (argument.contains(metacharacter)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String relativeDirectory(String value) {
        String relative = value.strip();
        for (String prefix : List.of("${project.basedir}", "${basedir}", "${project.build.directory}")) {
            if (relative.startsWith(prefix)) {
                relative = relative.substring(prefix.length());
                break;
            }
        }
        while (relative.startsWith("/") || relative.startsWith("./")) {
            relative = relative.startsWith("./") ? relative.substring(2) : relative.substring(1);
        }
        return relative.isBlank() ? "." : relative;
    }

    private static String kind(String artifactId) {
        String normalized = artifactId.toLowerCase();
        if (normalized.equals("exec-maven-plugin")) {
            return "exec";
        }
        if (normalized.equals("frontend-maven-plugin")) {
            return "frontend";
        }
        if (normalized.equals("maven-antrun-plugin")) {
            return "antrun";
        }
        return null;
    }
}
