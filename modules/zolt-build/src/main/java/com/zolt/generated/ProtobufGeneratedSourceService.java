package com.zolt.generated;

import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import com.zolt.project.ProtobufGenerationSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ProtobufGeneratedSourceService {
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*;");
    private static final Pattern JAVA_PACKAGE = Pattern.compile("(?m)^\\s*option\\s+java_package\\s*=\\s*\"([^\"]+)\"\\s*;");
    private static final Pattern MESSAGE = Pattern.compile("(?m)^\\s*message\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{");
    private static final Pattern SERVICE = Pattern.compile("(?m)^\\s*service\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{");

    public void generateMain(Path projectDirectory, ProjectConfig config) {
        generate(projectDirectory, "main", config.build().generatedMainSources());
    }

    public void generateTest(Path projectDirectory, ProjectConfig config) {
        generate(projectDirectory, "test", config.build().generatedTestSources());
    }

    private void generate(Path projectDirectory, String scope, List<GeneratedSourceStep> steps) {
        List<GeneratedSourceStep> protobufSteps = steps.stream()
                .filter(step -> step.kind() == GeneratedSourceKind.PROTOBUF)
                .toList();
        if (protobufSteps.isEmpty()) {
            return;
        }
        Path root = projectDirectory.toAbsolutePath().normalize();
        for (GeneratedSourceStep step : protobufSteps) {
            generateStep(root, scope, step);
        }
    }

    private static void generateStep(Path root, String scope, GeneratedSourceStep step) {
        validateStep(root, scope, step);
        Path output = outputPath(root, scope, step);
        List<ProtoFile> protoFiles = step.inputs().stream()
                .sorted()
                .map(input -> protoFile(root, step, scope, input))
                .toList();
        deleteOutput(output);
        createDirectory(output);
        for (ProtoFile protoFile : protoFiles) {
            String javaPackage = javaPackage(step.protobuf(), protoFile);
            Path packageRoot = packageRoot(output, javaPackage);
            createDirectory(packageRoot);
            for (String message : protoFile.messages()) {
                write(packageRoot.resolve(message + ".java"), messageSource(javaPackage, message));
            }
            if (step.protobuf().grpc()) {
                for (String service : protoFile.services()) {
                    write(packageRoot.resolve(service + "Grpc.java"), grpcSource(javaPackage, protoFile.protoPackage(), service));
                }
            }
        }
        writeDescriptor(output, step, protoFiles);
    }

    private static void validateStep(Path root, String scope, GeneratedSourceStep step) {
        if (!"java".equals(step.language())) {
            throw new GeneratedSourceException(
                    "Protobuf generated source step [generated."
                            + scope
                            + "."
                            + step.id()
                            + "] uses unsupported language `"
                            + step.language()
                            + "`. Zolt currently supports java.");
        }
        if (step.inputs().isEmpty()) {
            throw new GeneratedSourceException(
                    "Protobuf generated source step [generated."
                            + scope
                            + "."
                            + step.id()
                            + "] requires at least one .proto input.");
        }
        outputPath(root, scope, step);
        for (String input : step.inputs()) {
            Path path = inputPath(root, scope, step.id(), input);
            if (!Files.isRegularFile(path)) {
                throw new GeneratedSourceException(
                        "Protobuf input "
                                + input
                                + " does not exist for [generated."
                                + scope
                                + "."
                                + step.id()
                                + "]. Add the file or remove the generated-source step.");
            }
        }
    }

    private static ProtoFile protoFile(Path root, GeneratedSourceStep step, String scope, String input) {
        Path path = inputPath(root, scope, step.id(), input);
        String content;
        try {
            content = Files.readString(path);
        } catch (IOException exception) {
            throw new GeneratedSourceException("Could not read Protobuf input " + input + ".", exception);
        }
        String protoPackage = first(PACKAGE, content).orElse("");
        String javaPackage = first(JAVA_PACKAGE, content).orElse("");
        List<String> messages = matches(MESSAGE, content);
        List<String> services = matches(SERVICE, content);
        if (messages.isEmpty() && services.isEmpty()) {
            throw new GeneratedSourceException(
                    "Protobuf input "
                            + input
                            + " does not declare any message or service types. "
                            + "Add a message/service or remove [generated."
                            + scope
                            + "."
                            + step.id()
                            + "].");
        }
        return new ProtoFile(input, protoPackage, javaPackage, messages, services);
    }

    private static String javaPackage(ProtobufGenerationSettings settings, ProtoFile protoFile) {
        return settings.javaPackage()
                .filter(value -> !value.isBlank())
                .or(() -> Optional.of(protoFile.javaPackage()).filter(value -> !value.isBlank()))
                .or(() -> Optional.of(protoFile.protoPackage()).filter(value -> !value.isBlank()))
                .orElse("generated.protobuf");
    }

    private static Path packageRoot(Path output, String javaPackage) {
        return output.resolve(javaPackage.replace('.', '/'));
    }

    private static String messageSource(String javaPackage, String message) {
        return packageLine(javaPackage)
                + "\n"
                + "public final class "
                + message
                + " {\n"
                + "    public "
                + message
                + "() {\n"
                + "    }\n\n"
                + "    public static "
                + message
                + " getDefaultInstance() {\n"
                + "        return new "
                + message
                + "();\n"
                + "    }\n"
                + "}\n";
    }

    private static String grpcSource(String javaPackage, String protoPackage, String service) {
        String serviceName = protoPackage == null || protoPackage.isBlank() ? service : protoPackage + "." + service;
        return packageLine(javaPackage)
                + "\n"
                + "public final class "
                + service
                + "Grpc {\n"
                + "    private "
                + service
                + "Grpc() {\n"
                + "    }\n\n"
                + "    public static String serviceName() {\n"
                + "        return \""
                + serviceName
                + "\";\n"
                + "    }\n"
                + "}\n";
    }

    private static String packageLine(String javaPackage) {
        return javaPackage == null || javaPackage.isBlank() ? "" : "package " + javaPackage + ";\n";
    }

    private static void writeDescriptor(Path output, GeneratedSourceStep step, List<ProtoFile> protoFiles) {
        Path descriptor = output.resolve("META-INF/zolt/protobuf/" + step.id() + ".descriptor");
        StringBuilder content = new StringBuilder();
        content.append("id=").append(step.id()).append('\n');
        for (ProtoFile protoFile : protoFiles) {
            content.append("input=").append(protoFile.input()).append('\n');
            content.append("package=").append(protoFile.protoPackage()).append('\n');
            content.append("javaPackage=").append(javaPackage(step.protobuf(), protoFile)).append('\n');
            content.append("messages=").append(String.join(",", protoFile.messages())).append('\n');
            content.append("services=").append(String.join(",", protoFile.services())).append('\n');
        }
        write(descriptor, content.toString());
    }

    private static List<String> matches(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return List.copyOf(values);
    }

    private static Optional<String> first(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static Path outputPath(Path root, String scope, GeneratedSourceStep step) {
        try {
            return ProjectPaths.output(root, "[generated." + scope + "." + step.id() + "].output", step.output());
        } catch (ProjectPathException exception) {
            throw new GeneratedSourceException(exception.getMessage(), exception);
        }
    }

    private static Path inputPath(Path root, String scope, String id, String input) {
        try {
            return ProjectPaths.input(root, "[generated." + scope + "." + id + "].inputs", input);
        } catch (ProjectPathException exception) {
            throw new GeneratedSourceException(exception.getMessage(), exception);
        }
    }

    private static void deleteOutput(Path output) {
        if (!Files.exists(output)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(output)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException exception) {
            throw new GeneratedSourceException(
                    "Could not clean Protobuf output "
                            + output
                            + ". Check filesystem permissions and retry `zolt build`.",
                    exception);
        }
    }

    private static void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new GeneratedSourceException("Could not create Protobuf output directory " + path + ".", exception);
        }
    }

    private static void write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException exception) {
            throw new GeneratedSourceException("Could not write Protobuf generated file " + path + ".", exception);
        }
    }

    private record ProtoFile(
            String input,
            String protoPackage,
            String javaPackage,
            List<String> messages,
            List<String> services) {
        private ProtoFile {
            messages = new ArrayList<>(messages).stream().sorted().toList();
            services = new ArrayList<>(services).stream().sorted().toList();
        }
    }
}
