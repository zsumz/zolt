package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

final class PackageSupplementalArtifactAssembler {
    private final ClasspathBuilder classpathBuilder;

    PackageSupplementalArtifactAssembler(ClasspathBuilder classpathBuilder) {
        this.classpathBuilder = classpathBuilder;
    }

    List<PackageArtifact> assemble(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Optional<List<ResolvedClasspathPackage>> classpathPackages,
            Optional<ClasspathSet> classpaths) {
        List<PackageArtifact> artifacts = new ArrayList<>();
        if (config.packageSettings().sources()) {
            artifacts.add(packageSourcesJar(projectDirectory, config));
        }
        if (config.packageSettings().javadoc()) {
            artifacts.add(packageJavadocJar(projectDirectory, config, buildResult, classpathPackages, classpaths));
        }
        if (config.packageSettings().tests()) {
            artifacts.add(packageTestJar(projectDirectory, config));
        }
        return List.copyOf(artifacts);
    }

    private static PackageArtifact packageSourcesJar(Path projectDirectory, ProjectConfig config) {
        Path sourceRoot = ProjectPaths.existingRoot(projectDirectory, "[build].source", config.build().source());
        Path jarPath = classifierJarPath(projectDirectory, config, "sources");
        try {
            Files.createDirectories(jarPath.getParent());
            List<Path> files = PackageSupplementalArtifactFiles.sourceFiles(sourceRoot);
            PackageArchiveWriter.writeJarFromFiles(jarPath, sourceRoot, files);
            return new PackageArtifact("sources", jarPath, files.size());
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not package sources jar at "
                            + jarPath
                            + ". Check that source files are readable and target/ is writable.",
                    exception);
        }
    }

    private PackageArtifact packageJavadocJar(
            Path projectDirectory,
            ProjectConfig config,
            BuildResult buildResult,
            Optional<List<ResolvedClasspathPackage>> classpathPackages,
            Optional<ClasspathSet> classpaths) {
        Path sourceRoot = ProjectPaths.existingRoot(projectDirectory, "[build].source", config.build().source());
        Path javadocDirectory = ProjectPaths.output(
                projectDirectory,
                "package javadoc output",
                config.build().outputRoot() + "/javadoc");
        Path jarPath = classifierJarPath(projectDirectory, config, "javadoc");
        try {
            Files.createDirectories(jarPath.getParent());
            PackageSupplementalArtifactFiles.deleteDirectory(javadocDirectory);
            Files.createDirectories(javadocDirectory);
            List<Path> sources = PackageSupplementalArtifactFiles.sourceFiles(sourceRoot);
            if (!sources.isEmpty()) {
                runJavadoc(
                        projectDirectory,
                        sourceRoot,
                        javadocDirectory,
                        sources,
                        javadocClasspath(buildResult, classpathPackages, classpaths));
            }
            List<Path> files = PackageSupplementalArtifactFiles.regularFiles(javadocDirectory);
            PackageArchiveWriter.writeJarFromFiles(jarPath, javadocDirectory, files);
            return new PackageArtifact("javadoc", jarPath, files.size());
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not package javadoc jar at "
                            + jarPath
                            + ". Check that target/ is writable and retry.",
                    exception);
        }
    }

    private static PackageArtifact packageTestJar(Path projectDirectory, ProjectConfig config) {
        Path testOutput = ProjectPaths.output(projectDirectory, "[build].testOutput", config.build().testOutput());
        Path jarPath = classifierJarPath(projectDirectory, config, "tests");
        if (!Files.isDirectory(testOutput)) {
            throw new PackageException(
                    "Cannot package test jar because compiled test output is missing at "
                            + testOutput
                            + ". Run `zolt test` first, then retry `zolt package`.");
        }
        try {
            Files.createDirectories(jarPath.getParent());
            List<Path> files = PackageSupplementalArtifactFiles.compiledFiles(testOutput);
            PackageArchiveWriter.writeJarFromFiles(jarPath, testOutput, files);
            return new PackageArtifact("tests", jarPath, files.size());
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not package test jar at "
                            + jarPath
                            + ". Check that compiled test output is readable and target/ is writable.",
                    exception);
        }
    }

    private List<Path> javadocClasspath(
            BuildResult buildResult,
            Optional<List<ResolvedClasspathPackage>> classpathPackages,
            Optional<ClasspathSet> classpaths) {
        List<Path> entries = new ArrayList<>();
        entries.add(buildResult.outputDirectory());
        if (classpaths.isPresent()) {
            entries.addAll(classpaths.orElseThrow().compile().entries());
        } else {
            classpathPackages
                    .map(classpathBuilder::build)
                    .ifPresent(resolvedClasspaths -> entries.addAll(resolvedClasspaths.compile().entries()));
        }
        return entries.stream().map(Path::toAbsolutePath).map(Path::normalize).toList();
    }

    private static void runJavadoc(
            Path projectDirectory,
            Path sourceRoot,
            Path outputDirectory,
            List<Path> sources,
            List<Path> classpath) {
        List<String> command = new ArrayList<>();
        command.add(javadocExecutable().toString());
        command.add("-quiet");
        command.add("-d");
        command.add(outputDirectory.toString());
        command.add("-sourcepath");
        command.add(sourceRoot.toString());
        if (!classpath.isEmpty()) {
            command.add("-classpath");
            command.add(classpath.stream()
                    .map(Path::toString)
                    .collect(Collectors.joining(java.io.File.pathSeparator)));
        }
        sources.stream().map(Path::toString).forEach(command::add);
        try {
            Process process = new ProcessBuilder(command)
                    .directory(projectDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new PackageException(
                        "javadoc failed with exit code "
                                + exitCode
                                + ". Fix Javadoc errors or disable [package].javadoc, then retry.\n"
                                + output.stripTrailing());
            }
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not run javadoc. Check that the configured JDK includes the javadoc tool.",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PackageException("javadoc was interrupted. Try packaging again.", exception);
        }
    }

    private static Path javadocExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", executable("javadoc"));
    }

    private static String executable(String name) {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? name + ".exe" : name;
    }

    private static Path classifierJarPath(Path projectDirectory, ProjectConfig config, String classifier) {
        return ProjectPaths.output(
                projectDirectory,
                "package artifact",
                config.build().outputRoot() + "/" + artifactBaseName(config) + "-" + classifier + ".jar");
    }

    private static String artifactBaseName(ProjectConfig config) {
        return ProjectPaths.filenameComponent("[project].name", config.project().name())
                + "-"
                + ProjectPaths.filenameComponent("[project].version", config.project().version());
    }

}
