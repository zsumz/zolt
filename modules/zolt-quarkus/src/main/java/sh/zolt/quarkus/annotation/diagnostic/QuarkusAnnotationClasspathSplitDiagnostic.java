package sh.zolt.quarkus.annotation.diagnostic;

import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.annotation.launcher.QuarkusAnnotationLaunchRequest;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptor;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptorReader;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class QuarkusAnnotationClasspathSplitDiagnostic {
    private final BootstrapDescriptorReader bootstrapDescriptorReader;

    public QuarkusAnnotationClasspathSplitDiagnostic() {
        this(new QuarkusBootstrapDescriptorReader()::read);
    }

    QuarkusAnnotationClasspathSplitDiagnostic(BootstrapDescriptorReader bootstrapDescriptorReader) {
        if (bootstrapDescriptorReader == null) {
            throw new QuarkusAugmentationException(
                    "Quarkus annotation classpath split diagnostic bootstrap descriptor reader is required.");
        }
        this.bootstrapDescriptorReader = bootstrapDescriptorReader;
    }

    public String describe(QuarkusAnnotationLaunchRequest request) {
        if (request == null) {
            throw new QuarkusAugmentationException("Quarkus annotation classpath split diagnostic requires a request.");
        }
        try {
            QuarkusBootstrapDescriptor bootstrapDescriptor =
                    bootstrapDescriptorReader.read(request.descriptor().bootstrapDescriptorFile());
            List<Path> sharedClasspath = sharedClasspath(
                    request.descriptor().testRuntimeClasspath(),
                    bootstrapDescriptor.deploymentClasspath());
            return description(sharedClasspath);
        } catch (QuarkusAugmentationException exception) {
            return "Could not inspect Quarkus deployment classpath ownership from "
                    + request.descriptor().bootstrapDescriptorFile()
                    + ". Run `zolt build`, then run `zolt test` again.";
        }
    }

    public String describeMissingBuilderApi(QuarkusAnnotationLaunchRequest request) {
        if (request == null) {
            throw new QuarkusAugmentationException("Quarkus annotation classpath split diagnostic requires a request.");
        }
        return "Classpath ownership: "
                + quarkusBuilderLauncherOwnership(request.launcherClasspath())
                + " Quarkus JUnit needs io.quarkus.builder.item.MultiBuildItem while preparing test augmentation.";
    }

    public String describeRuntimeServiceProviderSplit(QuarkusAnnotationLaunchRequest request) {
        if (request == null) {
            throw new QuarkusAugmentationException("Quarkus annotation classpath split diagnostic requires a request.");
        }
        List<Path> sharedClasspath = sharedClasspathIfReadable(request);
        return "Classpath ownership: "
                + runtimeServiceTypeOwnership(request.launcherClasspath(), sharedClasspath)
                + " "
                + runtimeServiceProviderOwnership(request.launcherClasspath(), sharedClasspath)
                + " Quarkus JUnit reached application startup and needs TestHttpEndpointProvider service loading "
                + "to use one classloader identity for both the service type and provider. Zolt's programmatic "
                + "JUnit launcher boundary now keeps Quarkus condition/config evaluation on the system "
                + "classloader and defers the runtime context-classloader handoff until JUnit starts the "
                + "selected class source, so the current blocking failure is runtime service loading.";
    }

    private static String description(List<Path> sharedClasspath) {
        return "Classpath ownership: "
                + quarkusBuilderOwnership(sharedClasspath)
                + " Shared test/deployment classpath entries: "
                + sharedClasspath.size()
                + ".";
    }

    private static String quarkusBuilderOwnership(List<Path> sharedClasspath) {
        return sharedClasspath.stream()
                .filter(QuarkusAnnotationClasspathSplitDiagnostic::isQuarkusBuilderJar)
                .findFirst()
                .map(path -> fileName(path)
                        + " is present on both the JUnit test runtime classpath and Quarkus deployment classpath.")
                .orElse("quarkus-builder was not found in both classpaths.");
    }

    private static String quarkusBuilderLauncherOwnership(List<Path> launcherClasspath) {
        return launcherClasspath.stream()
                .filter(QuarkusAnnotationClasspathSplitDiagnostic::isQuarkusBuilderJar)
                .findFirst()
                .map(path -> fileName(path) + " is present on the annotation JVM launcher classpath.")
                .orElse("quarkus-builder is absent from the annotation JVM launcher classpath.");
    }

    private List<Path> sharedClasspathIfReadable(QuarkusAnnotationLaunchRequest request) {
        try {
            QuarkusBootstrapDescriptor bootstrapDescriptor =
                    bootstrapDescriptorReader.read(request.descriptor().bootstrapDescriptorFile());
            return sharedClasspath(request.descriptor().testRuntimeClasspath(), bootstrapDescriptor.deploymentClasspath());
        } catch (QuarkusAugmentationException exception) {
            return List.of();
        }
    }

    private static String runtimeServiceTypeOwnership(List<Path> launcherClasspath, List<Path> sharedClasspath) {
        return launcherClasspath.stream()
                .filter(QuarkusAnnotationClasspathSplitDiagnostic::isQuarkusCoreJar)
                .findFirst()
                .map(path -> fileName(path)
                        + " provides io.quarkus.runtime.test.TestHttpEndpointProvider on the annotation JVM launcher "
                        + "classpath"
                        + sharedSuffix(path, sharedClasspath))
                .orElse("quarkus-core was not found on the annotation JVM launcher classpath.");
    }

    private static String runtimeServiceProviderOwnership(List<Path> launcherClasspath, List<Path> sharedClasspath) {
        return launcherClasspath.stream()
                .filter(QuarkusAnnotationClasspathSplitDiagnostic::isQuarkusRestJar)
                .findFirst()
                .map(path -> fileName(path)
                        + " provides io.quarkus.resteasy.reactive.server.runtime.ResteasyReactiveTestHttpProvider "
                        + "through META-INF/services"
                        + sharedSuffix(path, sharedClasspath))
                .orElse("quarkus-rest was not found on the annotation JVM launcher classpath.");
    }

    private static String sharedSuffix(Path path, List<Path> sharedClasspath) {
        return sharedClasspath.contains(normalize(path))
                ? " and is also present on the Quarkus deployment classpath."
                : ".";
    }

    private static List<Path> sharedClasspath(List<Path> testRuntimeClasspath, List<Path> deploymentClasspath) {
        Set<Path> deploymentEntries = new LinkedHashSet<>();
        for (Path entry : deploymentClasspath) {
            deploymentEntries.add(normalize(entry));
        }
        Set<Path> shared = new LinkedHashSet<>();
        for (Path entry : testRuntimeClasspath) {
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

    private static boolean isQuarkusCoreJar(Path path) {
        return isVersionedArtifactJar(path, "quarkus-core-");
    }

    private static boolean isQuarkusRestJar(Path path) {
        return isVersionedArtifactJar(path, "quarkus-rest-");
    }

    private static boolean isVersionedArtifactJar(Path path, String prefix) {
        String fileName = fileName(path);
        return fileName.startsWith(prefix)
                && fileName.endsWith(".jar")
                && fileName.length() > prefix.length()
                && Character.isDigit(fileName.charAt(prefix.length()));
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
}
