package com.zolt.build;

import com.zolt.classpath.Classpath;
import com.zolt.doctor.JdkStatus;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class SpringBootAotGenerationService {
    private final JavacRunner javacRunner;

    SpringBootAotGenerationService(JavacRunner javacRunner) {
        this.javacRunner = javacRunner;
    }

    void generate(Path projectDirectory, ProjectConfig config, JdkStatus jdkStatus) {
        if (!config.frameworkSettings().springBoot().nativeEnabled()) {
            return;
        }
        Path root = projectDirectory.toAbsolutePath().normalize();
        Path outputRoot = outputRoot(root, config);
        Path sourcesRoot = outputRoot.resolve("sources");
        Path resourcesRoot = outputRoot.resolve("resources");
        Path classesRoot = outputRoot.resolve("classes");
        deleteOutput(outputRoot);
        Path source = sourcesRoot.resolve("com/zolt/springaot/ZoltSpringAotMarker.java");
        write(source, sourceContent(config));
        write(resourcesRoot.resolve("META-INF/native-image/" + metadataPath(config) + "/reflect-config.json"), "[]\n");
        write(resourcesRoot.resolve("META-INF/native-image/" + metadataPath(config) + "/resource-config.json"), """
                {
                  "resources": {
                    "includes": []
                  }
                }
                """);
        javacRunner.compile(
                jdkStatus.javac().orElseThrow(() -> new JavacException(
                        "Could not compile Spring Boot AOT outputs because javac is missing.")),
                List.of(source),
                new Classpath(List.of()),
                classesRoot);
    }

    private static Path outputRoot(Path root, ProjectConfig config) {
        try {
            return ProjectPaths.output(
                    root,
                    "Spring Boot AOT output",
                    config.build().outputRoot() + "/spring-aot/main");
        } catch (ProjectPathException exception) {
            throw new BuildException(exception.getMessage(), exception);
        }
    }

    private static String sourceContent(ProjectConfig config) {
        return """
                package com.zolt.springaot;

                public final class ZoltSpringAotMarker {
                    private ZoltSpringAotMarker() {
                    }

                    public static String application() {
                        return "%s";
                    }
                }
                """.formatted(javaString(config.project().group()
                + ":"
                + config.project().name()
                + ":"
                + config.project().version()));
    }

    private static String metadataPath(ProjectConfig config) {
        return safePathComponent(config.project().group()) + "/" + safePathComponent(config.project().name());
    }

    private static String safePathComponent(String value) {
        StringBuilder safe = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if ((current >= 'a' && current <= 'z')
                    || (current >= 'A' && current <= 'Z')
                    || (current >= '0' && current <= '9')
                    || current == '.'
                    || current == '_'
                    || current == '-') {
                safe.append(current);
            } else {
                safe.append('_');
            }
        }
        return safe.isEmpty() ? "app" : safe.toString();
    }

    private static String javaString(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void deleteOutput(Path outputRoot) {
        if (!Files.exists(outputRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(outputRoot)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not clean Spring Boot AOT output "
                            + outputRoot
                            + ". Check filesystem permissions and retry `zolt build`.",
                    exception);
        }
    }

    private static void write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException exception) {
            throw new BuildException(
                    "Could not write Spring Boot AOT output "
                            + path
                            + ". Check filesystem permissions and retry `zolt build`.",
                    exception);
        }
    }
}
