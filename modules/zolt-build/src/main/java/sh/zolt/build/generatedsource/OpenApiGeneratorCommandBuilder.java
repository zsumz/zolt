package sh.zolt.build.generatedsource;

import static sh.zolt.build.generatedsource.OpenApiGeneratedSourcePaths.inputPath;
import static sh.zolt.build.generatedsource.OpenApiGeneratedSourcePaths.outputPath;

import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.OpenApiGenerationSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

final class OpenApiGeneratorCommandBuilder {
    private static final String MAIN_CLASS = "org.openapitools.codegen.OpenAPIGenerator";

    private final String pathSeparator;

    OpenApiGeneratorCommandBuilder(String pathSeparator) {
        this.pathSeparator = pathSeparator;
    }

    List<String> command(
            Path projectRoot,
            Path javaExecutable,
            List<Path> toolClasspath,
            String scope,
            GeneratedSourceStep step) {
        OpenApiGenerationSettings settings = step.openApi();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.add("-cp");
        command.add(joinClasspath(toolClasspath));
        command.add(MAIN_CLASS);
        command.add("generate");
        command.add("--input-spec");
        command.add(inputPath(projectRoot, step.inputs().getFirst(), scope, step.id(), "input").toString());
        command.add("--generator-name");
        command.add(settings.generator().orElseThrow());
        command.add("--output");
        command.add(outputPath(projectRoot, step.output(), scope, step.id(), "output").toString());
        if (settings.validateSpec().isPresent() && !settings.validateSpec().orElseThrow()) {
            command.add("--skip-validate-spec");
        }
        settings.library().ifPresent(value -> addOption(command, "--library", value));
        settings.apiPackage().ifPresent(value -> addOption(command, "--api-package", value));
        settings.modelPackage().ifPresent(value -> addOption(command, "--model-package", value));
        settings.invokerPackage().ifPresent(value -> addOption(command, "--invoker-package", value));
        settings.config().ifPresent(value -> addOption(
                command,
                "--config",
                inputPath(projectRoot, value, scope, step.id(), "config").toString()));
        settings.templateDir().ifPresent(value -> addOption(
                command,
                "--template-dir",
                inputPath(projectRoot, value, scope, step.id(), "templateDir").toString()));
        String additionalProperties = joinedMap(additionalProperties(settings));
        if (!additionalProperties.isBlank()) {
            addOption(command, "--additional-properties", additionalProperties);
        }
        addMapOption(command, "--global-property", settings.globalProperties());
        addMapOption(command, "--type-mappings", settings.typeMappings());
        addMapOption(command, "--import-mappings", settings.importMappings());
        return List.copyOf(command);
    }

    private static void addOption(List<String> command, String name, String value) {
        command.add(name);
        command.add(value);
    }

    private static void addMapOption(List<String> command, String name, Map<String, String> values) {
        String joined = joinedMap(values);
        if (!joined.isBlank()) {
            addOption(command, name, joined);
        }
    }

    private static Map<String, String> additionalProperties(OpenApiGenerationSettings settings) {
        Map<String, String> values = new LinkedHashMap<>();
        values.putAll(settings.options());
        values.putAll(settings.additionalProperties());
        values.putAll(settings.configOptions());
        return values;
    }

    private static String joinedMap(Map<String, String> values) {
        if (values.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(",");
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> joiner.add(entry.getKey() + "=" + entry.getValue()));
        return joiner.toString();
    }

    private String joinClasspath(List<Path> paths) {
        StringJoiner joiner = new StringJoiner(pathSeparator);
        paths.stream()
                .map(path -> path.toAbsolutePath().normalize().toString())
                .sorted()
                .forEach(joiner::add);
        return joiner.toString();
    }
}
