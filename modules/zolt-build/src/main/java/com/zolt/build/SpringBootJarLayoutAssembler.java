package com.zolt.build;

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

final class SpringBootJarLayoutAssembler {
    private static final String BOOT_INF_PREFIX = "BOOT-INF/";
    private static final String BOOT_CLASSES_PREFIX = "BOOT-INF/classes/";
    private static final String BOOT_LIB_PREFIX = "BOOT-INF/lib/";
    private static final Set<String> LOCAL_BUILD_FINGERPRINTS = Set.of(
            ".zolt-build-main.fingerprint",
            ".zolt-build-main.fingerprint.state",
            ".zolt-build-test.fingerprint",
            ".zolt-build-test.fingerprint.state",
            ".zolt-incremental-main.state",
            ".zolt-incremental-test.state");

    PackageResult assemble(
            String startClass,
            BuildResult buildResult,
            Path outputDirectory,
            Path jarPath,
            List<PackageRuntimeJar> runtimeJars) {
        SpringBootLoaderSupport.SpringBootLoader loader = SpringBootLoaderSupport.jarLoader(runtimeJars);

        try {
            Files.createDirectories(jarPath.getParent());
            List<Path> files = compiledFiles(outputDirectory);
            try (PackageArchiveWriter archive = PackageArchiveWriter.open(jarPath)) {
                archive.writeEntry(GeneratedManifest.DEFAULT_PATH, springBootManifest(startClass, loader));
                archive.writeDirectory(BOOT_INF_PREFIX);
                archive.writeDirectory(BOOT_CLASSES_PREFIX);
                archive.writeDirectory(BOOT_LIB_PREFIX);
                for (var entry : loader.entries().entrySet()) {
                    archive.writeParentDirectories(entry.getKey());
                    archive.writeEntry(entry.getKey(), entry.getValue());
                }
                for (Path file : files) {
                    String bootEntryName = BOOT_CLASSES_PREFIX + entryName(outputDirectory, file);
                    archive.writeParentDirectories(bootEntryName);
                    archive.writeFile(bootEntryName, file);
                }
                for (PackageRuntimeJar runtimeJar : runtimeJars) {
                    if (runtimeJar.packageId().equals(SpringBootLoaderSupport.SPRING_BOOT_LOADER_PACKAGE)) {
                        continue;
                    }
                    archive.writeStoredEntry(
                            BOOT_LIB_PREFIX + PackageRuntimeJars.nestedJarName(runtimeJar),
                            PackageRuntimeJars.read(runtimeJar));
                }
            }
            return new PackageResult(
                    buildResult,
                    PackageMode.SPRING_BOOT,
                    jarPath,
                    Optional.empty(),
                    files.size(),
                    true);
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not package Spring Boot jar at "
                            + jarPath
                            + ". Check that target/ is writable and try again.",
                    exception);
        }
    }

    private static byte[] springBootManifest(
            String startClass,
            SpringBootLoaderSupport.SpringBootLoader loader) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.MAIN_CLASS, loader.launcherClass());
        attributes.put(new Attributes.Name("Start-Class"), startClass);
        attributes.put(new Attributes.Name("Spring-Boot-Version"), loader.jar().version());
        attributes.put(new Attributes.Name("Spring-Boot-Classes"), BOOT_CLASSES_PREFIX);
        attributes.put(new Attributes.Name("Spring-Boot-Lib"), BOOT_LIB_PREFIX);

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
