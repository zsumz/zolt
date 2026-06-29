package com.zolt.build.springboot;

import com.zolt.build.BuildResult;
import com.zolt.build.GeneratedManifest;
import com.zolt.build.PackageException;
import com.zolt.build.PackageResult;
import com.zolt.build.packaging.PackageArchiveWriter;
import com.zolt.build.packaging.PackageRuntimeJar;
import com.zolt.build.packaging.PackageRuntimeJars;
import com.zolt.project.PackageMode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public final class SpringBootWarLayoutAssembler {
    private static final String WEB_INF_PREFIX = "WEB-INF/";
    private static final String WEB_INF_CLASSES_PREFIX = "WEB-INF/classes/";
    private static final String WEB_INF_LIB_PREFIX = "WEB-INF/lib/";
    private static final String WEB_INF_LIB_PROVIDED_PREFIX = "WEB-INF/lib-provided/";
    private static final Set<String> LOCAL_BUILD_FINGERPRINTS = Set.of(
            ".zolt-build-main.fingerprint",
            ".zolt-build-main.fingerprint.state",
            ".zolt-build-test.fingerprint",
            ".zolt-build-test.fingerprint.state",
            ".zolt-incremental-main.state",
            ".zolt-incremental-test.state");

    public PackageResult assemble(
            String startClass,
            BuildResult buildResult,
            Path outputDirectory,
            Path warPath,
            List<PackageRuntimeJar> runtimeJars,
            List<PackageRuntimeJar> providedJars) {
        SpringBootLoaderSupport.SpringBootLoader loader = SpringBootLoaderSupport.warLoader(runtimeJars);

        try {
            Files.createDirectories(warPath.getParent());
            List<Path> files = compiledFiles(outputDirectory);
            try (PackageArchiveWriter archive = PackageArchiveWriter.open(warPath)) {
                archive.writeEntry(GeneratedManifest.DEFAULT_PATH, springBootWarManifest(startClass, loader));
                archive.writeDirectory(WEB_INF_PREFIX);
                archive.writeDirectory(WEB_INF_CLASSES_PREFIX);
                archive.writeDirectory(WEB_INF_LIB_PREFIX);
                if (!providedJars.isEmpty()) {
                    archive.writeDirectory(WEB_INF_LIB_PROVIDED_PREFIX);
                }
                for (var entry : loader.entries().entrySet()) {
                    archive.writeParentDirectories(entry.getKey());
                    archive.writeEntry(entry.getKey(), entry.getValue());
                }
                for (Path file : files) {
                    String warEntryName = WEB_INF_CLASSES_PREFIX + entryName(outputDirectory, file);
                    archive.writeParentDirectories(warEntryName);
                    archive.writeFile(warEntryName, file);
                }
                for (PackageRuntimeJar runtimeJar : runtimeJars) {
                    if (runtimeJar.packageId().equals(SpringBootLoaderSupport.SPRING_BOOT_LOADER_PACKAGE)) {
                        continue;
                    }
                    archive.writeStoredEntry(
                            WEB_INF_LIB_PREFIX + PackageRuntimeJars.nestedJarName(runtimeJar),
                            PackageRuntimeJars.read(runtimeJar));
                }
                for (PackageRuntimeJar providedJar : providedJars) {
                    archive.writeStoredEntry(
                            WEB_INF_LIB_PROVIDED_PREFIX + PackageRuntimeJars.nestedJarName(providedJar),
                            PackageRuntimeJars.read(providedJar));
                }
            }
            return new PackageResult(
                    buildResult,
                    PackageMode.SPRING_BOOT_WAR,
                    warPath,
                    Optional.empty(),
                    files.size(),
                    true);
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not package Spring Boot WAR at "
                            + warPath
                            + ". Check that target/ is writable and try again.",
                    exception);
        }
    }

    private static byte[] springBootWarManifest(
            String startClass,
            SpringBootLoaderSupport.SpringBootLoader loader) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.MAIN_CLASS, loader.launcherClass());
        attributes.put(new Attributes.Name("Start-Class"), startClass);
        attributes.put(new Attributes.Name("Spring-Boot-Version"), loader.jar().version());
        attributes.put(new Attributes.Name("Spring-Boot-Classes"), WEB_INF_CLASSES_PREFIX);
        attributes.put(new Attributes.Name("Spring-Boot-Lib"), WEB_INF_LIB_PREFIX);
        attributes.put(new Attributes.Name("Spring-Boot-Lib-Provided"), WEB_INF_LIB_PROVIDED_PREFIX);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        manifest.write(output);
        return output.toByteArray();
    }

    private static List<Path> compiledFiles(Path outputDirectory) throws IOException {
        try (var stream = Files.walk(outputDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !LOCAL_BUILD_FINGERPRINTS.contains(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> entryName(outputDirectory, path)))
                    .toList();
        }
    }

    private static String entryName(Path outputDirectory, Path file) {
        return outputDirectory.relativize(file).normalize().toString().replace('\\', '/');
    }
}
