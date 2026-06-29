package com.zolt.quarkus.annotation;

import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.bootstrap.QuarkusBootstrapDescriptor;
import com.zolt.quarkus.bootstrap.QuarkusBootstrapDescriptorReader;
import com.zolt.quarkus.testworker.QuarkusTestRunnerDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class QuarkusAnnotationLauncherClasspathPlanner {
    private static final String LAUNCHER_SESSION_LISTENER_SERVICE =
            "META-INF/services/org.junit.platform.launcher.LauncherSessionListener";
    private static final String TEST_BUILD_CHAIN_CUSTOMIZER_SERVICE =
            "META-INF/services/io.quarkus.test.junit.buildchain.TestBuildChainCustomizerProducer";
    private static final String ZOLT_TEST_CLASS_BEAN_CUSTOMIZER =
            "com.zolt.quarkus.annotation.ZoltQuarkusTestClassBeanCustomizer";
    private static final String ZOLT_TEST_CLASS_BEAN_CUSTOMIZER_CLASS =
            "com/zolt/quarkus/annotation/ZoltQuarkusTestClassBeanCustomizer.class";

    private final BootstrapDescriptorReader bootstrapDescriptorReader;

    public QuarkusAnnotationLauncherClasspathPlanner() {
        this(new QuarkusBootstrapDescriptorReader()::read);
    }

    QuarkusAnnotationLauncherClasspathPlanner(BootstrapDescriptorReader bootstrapDescriptorReader) {
        if (bootstrapDescriptorReader == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation launcher classpath planner bootstrap descriptor reader is required.");
        }
        this.bootstrapDescriptorReader = bootstrapDescriptorReader;
    }

    public QuarkusAnnotationLauncherClasspathPlan plan(QuarkusTestRunnerDescriptor descriptor) {
        if (descriptor == null) {
            throw new QuarkusAugmentationException("Quarkus annotation launcher classpath planner requires a descriptor.");
        }
        ServiceFilteredClasspath serviceFilteredClasspath = serviceFilteredClasspath(descriptor);
        List<Path> launcherClasspath = serviceFilteredClasspath.classpath();
        List<Path> sharedClasspath = sharedClasspath(descriptor, launcherClasspath);
        return new QuarkusAnnotationLauncherClasspathPlan(
                launcherClasspath,
                launcherClasspath,
                serviceFilteredClasspath.filteredArtifacts(),
                splitSensitiveArtifacts(sharedClasspath),
                builderApiVisible(launcherClasspath),
                sharedClasspath.size());
    }

    private static ServiceFilteredClasspath serviceFilteredClasspath(QuarkusTestRunnerDescriptor descriptor) {
        List<Path> classpath = new ArrayList<>();
        List<Path> filteredArtifacts = new ArrayList<>();
        for (Path entry : descriptor.testRuntimeClasspath()) {
            if (isQuarkusJunitConfigJar(entry)) {
                Path filteredArtifact = filteredQuarkusJunitConfigJar(descriptor, entry);
                classpath.add(filteredArtifact);
                filteredArtifacts.add(filteredArtifact);
            } else if (isQuarkusJunitJar(entry)) {
                Path augmentedArtifact = augmentedQuarkusJunitJar(descriptor, entry);
                classpath.add(augmentedArtifact);
                filteredArtifacts.add(augmentedArtifact);
            } else {
                classpath.add(entry);
            }
        }
        return new ServiceFilteredClasspath(List.copyOf(classpath), List.copyOf(filteredArtifacts));
    }

    private static Path filteredQuarkusJunitConfigJar(QuarkusTestRunnerDescriptor descriptor, Path sourceJar) {
        Path outputDirectory = descriptor.descriptorFile()
                .getParent()
                .resolve("annotation-runner");
        Path outputJar = outputDirectory.resolve("service-filtered-" + fileName(sourceJar));
        try {
            Files.createDirectories(outputDirectory);
            Path temporaryJar = Files.createTempFile(outputDirectory, outputJar.getFileName().toString(), ".tmp");
            writeJarWithoutLauncherSessionListener(sourceJar, temporaryJar);
            Files.move(temporaryJar, outputJar, StandardCopyOption.REPLACE_EXISTING);
            return outputJar.toAbsolutePath().normalize();
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not prepare Quarkus annotation runner classpath. "
                            + "Zolt needs to filter the launcher-session service from "
                            + sourceJar
                            + " so Quarkus JUnit owns test configuration during @QuarkusTest bootstrap. "
                            + "Clean " + descriptor.descriptorFile().getParent() + " and run `zolt test` again.",
                    exception);
        }
    }

    private static void writeJarWithoutLauncherSessionListener(Path sourceJar, Path targetJar) throws IOException {
        try (InputStream input = Files.newInputStream(sourceJar);
                JarInputStream jarInput = new JarInputStream(input);
                OutputStream output = Files.newOutputStream(targetJar)) {
            Manifest manifest = jarInput.getManifest();
            JarOutputStream jarOutput = manifest == null
                    ? new JarOutputStream(output)
                    : new JarOutputStream(output, manifest);
            JarEntry entry = jarInput.getNextJarEntry();
            try (jarOutput) {
                while (entry != null) {
                    if (!LAUNCHER_SESSION_LISTENER_SERVICE.equals(entry.getName())) {
                        JarEntry copy = new JarEntry(entry.getName());
                        copy.setTime(0L);
                        jarOutput.putNextEntry(copy);
                        jarInput.transferTo(jarOutput);
                        jarOutput.closeEntry();
                    }
                    jarInput.closeEntry();
                    entry = jarInput.getNextJarEntry();
                }
            }
        }
    }

    private static Path augmentedQuarkusJunitJar(QuarkusTestRunnerDescriptor descriptor, Path sourceJar) {
        Path outputDirectory = descriptor.descriptorFile()
                .getParent()
                .resolve("annotation-runner");
        Path outputJar = outputDirectory.resolve("zolt-augmented-" + fileName(sourceJar));
        try {
            Files.createDirectories(outputDirectory);
            Path temporaryJar = Files.createTempFile(outputDirectory, outputJar.getFileName().toString(), ".tmp");
            writeJarWithZoltTestClassBeanCustomizer(sourceJar, temporaryJar);
            Files.move(temporaryJar, outputJar, StandardCopyOption.REPLACE_EXISTING);
            return outputJar.toAbsolutePath().normalize();
        } catch (IOException exception) {
            throw new QuarkusAugmentationException(
                    "Could not prepare Quarkus annotation runner classpath. "
                            + "Zolt needs to add its @QuarkusTest class-bean customizer to "
                            + sourceJar
                            + " so Quarkus test augmentation can see Zolt-owned test classes. "
                            + "Clean " + descriptor.descriptorFile().getParent() + " and run `zolt test` again.",
                    exception);
        }
    }

    private static void writeJarWithZoltTestClassBeanCustomizer(Path sourceJar, Path targetJar) throws IOException {
        try (InputStream input = Files.newInputStream(sourceJar);
                JarInputStream jarInput = new JarInputStream(input);
                OutputStream output = Files.newOutputStream(targetJar)) {
            Manifest manifest = jarInput.getManifest();
            JarOutputStream jarOutput = manifest == null
                    ? new JarOutputStream(output)
                    : new JarOutputStream(output, manifest);
            JarEntry entry = jarInput.getNextJarEntry();
            try (jarOutput) {
                while (entry != null) {
                    if (!TEST_BUILD_CHAIN_CUSTOMIZER_SERVICE.equals(entry.getName())
                            && !ZOLT_TEST_CLASS_BEAN_CUSTOMIZER_CLASS.equals(entry.getName())) {
                        JarEntry copy = new JarEntry(entry.getName());
                        copy.setTime(0L);
                        jarOutput.putNextEntry(copy);
                        jarInput.transferTo(jarOutput);
                        jarOutput.closeEntry();
                    }
                    jarInput.closeEntry();
                    entry = jarInput.getNextJarEntry();
                }
                writeEntry(jarOutput, TEST_BUILD_CHAIN_CUSTOMIZER_SERVICE, ZOLT_TEST_CLASS_BEAN_CUSTOMIZER + "\n");
                writeEntry(jarOutput, ZOLT_TEST_CLASS_BEAN_CUSTOMIZER_CLASS, zoltCustomizerClassBytes());
            }
        }
    }

    private static byte[] zoltCustomizerClassBytes() throws IOException {
        try (InputStream input = QuarkusAnnotationLauncherClasspathPlanner.class
                .getClassLoader()
                .getResourceAsStream(ZOLT_TEST_CLASS_BEAN_CUSTOMIZER_CLASS)) {
            if (input == null) {
                throw new IOException("Missing class resource " + ZOLT_TEST_CLASS_BEAN_CUSTOMIZER_CLASS);
            }
            return input.readAllBytes();
        }
    }

    private static void writeEntry(JarOutputStream jarOutput, String name, String content) throws IOException {
        writeEntry(jarOutput, name, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void writeEntry(JarOutputStream jarOutput, String name, byte[] content) throws IOException {
        JarEntry entry = new JarEntry(name);
        entry.setTime(0L);
        jarOutput.putNextEntry(entry);
        jarOutput.write(content);
        jarOutput.closeEntry();
    }

    private List<Path> sharedClasspath(
            QuarkusTestRunnerDescriptor descriptor,
            List<Path> launcherClasspath) {
        try {
            return sharedClasspath(
                    launcherClasspath,
                    bootstrapDescriptorReader.read(descriptor.bootstrapDescriptorFile()).deploymentClasspath());
        } catch (QuarkusAugmentationException exception) {
            return List.of();
        }
    }

    private static List<Path> splitSensitiveArtifacts(List<Path> sharedClasspath) {
        return sharedClasspath.stream()
                .filter(QuarkusAnnotationLauncherClasspathPlanner::isQuarkusBuilderJar)
                .toList();
    }

    private static boolean builderApiVisible(List<Path> launcherClasspath) {
        return launcherClasspath.stream().anyMatch(QuarkusAnnotationLauncherClasspathPlanner::isQuarkusBuilderJar);
    }

    private static List<Path> sharedClasspath(List<Path> launcherClasspath, List<Path> deploymentClasspath) {
        Set<Path> deploymentEntries = new LinkedHashSet<>();
        for (Path entry : deploymentClasspath) {
            deploymentEntries.add(normalize(entry));
        }
        Set<Path> shared = new LinkedHashSet<>();
        for (Path entry : launcherClasspath) {
            Path normalized = normalize(entry);
            if (deploymentEntries.contains(normalized)) {
                shared.add(normalized);
            }
        }
        return List.copyOf(shared);
    }

    private static boolean isQuarkusBuilderJar(Path path) {
        return fileName(path).startsWith("quarkus-builder-") && fileName(path).endsWith(".jar");
    }

    private static boolean isQuarkusJunitConfigJar(Path path) {
        return fileName(path).startsWith("quarkus-junit-config-") && fileName(path).endsWith(".jar");
    }

    private static boolean isQuarkusJunitJar(Path path) {
        String fileName = fileName(path);
        return fileName.startsWith("quarkus-junit-")
                && fileName.endsWith(".jar")
                && fileName.substring("quarkus-junit-".length()).matches("\\d.*");
    }

    private static String fileName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    @FunctionalInterface
    interface BootstrapDescriptorReader {
        QuarkusBootstrapDescriptor read(Path descriptorFile);
    }

    private record ServiceFilteredClasspath(
            List<Path> classpath,
            List<Path> filteredArtifacts) {
    }
}
