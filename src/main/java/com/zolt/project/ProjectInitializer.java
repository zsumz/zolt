package com.zolt.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class ProjectInitializer {
    private final ProjectConfigWriter writer;

    public ProjectInitializer(ProjectConfigWriter writer) {
        this.writer = writer;
    }

    public ProjectInitResult init(Path baseDirectory, String name, String group, String javaVersion) {
        validateProjectName(name);
        validateJavaPackage(group);
        if (javaVersion == null || javaVersion.isBlank()) {
            throw new ProjectInitException("Java version is required. Pass a non-empty --java value.");
        }

        Path projectDirectory = baseDirectory.resolve(name).normalize();
        if (Files.exists(projectDirectory) && directoryHasEntries(projectDirectory)) {
            throw new ProjectInitException(
                    "Project directory " + projectDirectory + " is not empty. Choose a new name or empty the directory.");
        }

        String mainClass = group + ".Main";
        ProjectConfig config = new ProjectConfig(
                new ProjectMetadata(name, "0.1.0", group, javaVersion, Optional.of(mainClass)),
                ProjectConfig.defaultRepositories(),
                java.util.Map.of(),
                java.util.Map.of(),
                BuildSettings.defaults());

        Path packagePath = Path.of(group.replace('.', '/'));
        Path mainSource = projectDirectory.resolve("src/main/java").resolve(packagePath).resolve("Main.java");
        Path testSource = projectDirectory.resolve("src/test/java").resolve(packagePath).resolve("MainTest.java");
        Path configFile = projectDirectory.resolve("zolt.toml");

        try {
            Files.createDirectories(mainSource.getParent());
            Files.createDirectories(testSource.getParent());
            writer.write(configFile, config);
            Files.writeString(mainSource, mainSource(name, group));
            Files.writeString(testSource, testSource(group));
            Files.writeString(projectDirectory.resolve(".gitignore"), gitignore());
        } catch (IOException | ProjectConfigWriteException exception) {
            throw new ProjectInitException(
                    "Could not create Zolt project at " + projectDirectory + ". Check filesystem permissions.");
        }

        return new ProjectInitResult(projectDirectory, configFile, mainSource, testSource);
    }

    private static boolean directoryHasEntries(Path directory) {
        try (var entries = Files.list(directory)) {
            return entries.findAny().isPresent();
        } catch (IOException exception) {
            throw new ProjectInitException(
                    "Could not inspect project directory " + directory + ". Check filesystem permissions.");
        }
    }

    private static String mainSource(String projectName, String group) {
        return """
                package %s;

                public final class Main {
                    private Main() {
                    }

                    public static void main(String[] args) {
                        System.out.println("Hello from %s!");
                    }
                }
                """.formatted(group, escapeJavaString(projectName));
    }

    private static String testSource(String group) {
        return """
                package %s;

                final class MainTest {
                }
                """.formatted(group);
    }

    private static String gitignore() {
        return """
                target/
                build/
                out/

                .DS_Store
                """;
    }

    private static void validateProjectName(String name) {
        if (name == null || name.isBlank()) {
            throw new ProjectInitException("Project name is required. Try `zolt init hello`.");
        }
        if (name.contains("/") || name.contains("\\")) {
            throw new ProjectInitException("Project name must be a directory name, not a path.");
        }
    }

    private static void validateJavaPackage(String group) {
        if (group == null || group.isBlank()) {
            throw new ProjectInitException("Project group is required. Pass a Java package-like --group value.");
        }

        for (String part : group.split("\\.")) {
            if (!isJavaIdentifier(part)) {
                throw new ProjectInitException(
                        "Project group must be a valid Java package, for example `com.example`.");
            }
        }
    }

    private static boolean isJavaIdentifier(String value) {
        if (value.isEmpty() || !Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            if (!Character.isJavaIdentifierPart(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static String escapeJavaString(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
