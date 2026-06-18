package com.zolt.build;

import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;

final class UberJarLayoutAssembler {
    private static final Set<String> LOCAL_BUILD_FINGERPRINTS = Set.of(
            ".zolt-build-main.fingerprint",
            ".zolt-build-main.fingerprint.state",
            ".zolt-build-test.fingerprint",
            ".zolt-build-test.fingerprint.state",
            ".zolt-incremental-main.state",
            ".zolt-incremental-test.state");

    private final ManifestGenerator manifestGenerator;

    UberJarLayoutAssembler(ManifestGenerator manifestGenerator) {
        this.manifestGenerator = manifestGenerator;
    }

    PackageResult assemble(
            ProjectConfig config,
            BuildResult buildResult,
            Path outputDirectory,
            Path jarPath,
            List<PackageRuntimeJar> runtimeJars) {
        GeneratedManifest manifest = manifestGenerator.generate(config);
        Set<String> entries = new HashSet<>();
        int entryCount = 0;
        try {
            Files.createDirectories(jarPath.getParent());
            try (PackageArchiveWriter archive = PackageArchiveWriter.open(jarPath)) {
                writeEntry(archive, entries, manifest.path(), manifest.content(), "generated manifest");
                entryCount++;
                for (Path file : compiledFiles(outputDirectory)) {
                    writeEntry(
                            archive,
                            entries,
                            entryName(outputDirectory, file),
                            Files.readAllBytes(file),
                            "application output");
                    entryCount++;
                }
                for (PackageRuntimeJar runtimeJar : runtimeJars) {
                    entryCount += mergeRuntimeJar(archive, entries, runtimeJar);
                }
            }
            return new PackageResult(
                    buildResult,
                    PackageMode.UBER,
                    jarPath,
                    java.util.Optional.empty(),
                    entryCount - 1,
                    manifest.mainClass().isPresent());
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not package uber jar at "
                            + jarPath
                            + ". Check that target/ is writable and try again.",
                    exception);
        }
    }

    private static int mergeRuntimeJar(
            PackageArchiveWriter archive,
            Set<String> entries,
            PackageRuntimeJar runtimeJar) throws IOException {
        int merged = 0;
        try (JarFile jar = new JarFile(runtimeJar.jarPath().toFile())) {
            List<java.util.jar.JarEntry> jarEntries = jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> !ignoredDependencyEntry(entry.getName()))
                    .sorted(Comparator.comparing(java.util.jar.JarEntry::getName))
                    .toList();
            for (java.util.jar.JarEntry entry : jarEntries) {
                byte[] content;
                try (InputStream input = jar.getInputStream(entry)) {
                    content = input.readAllBytes();
                }
                writeEntry(archive, entries, entry.getName(), content, runtimeJar.packageId().toString());
                merged++;
            }
        }
        return merged;
    }

    private static void writeEntry(
            PackageArchiveWriter archive,
            Set<String> entries,
            String name,
            byte[] content,
            String source) throws IOException {
        if (!entries.add(name)) {
            throw new PackageException(
                    "Duplicate uber jar entry `"
                            + name
                            + "` while merging "
                            + source
                            + ". Move one dependency out of the runtime classpath or use `thin` package mode.");
        }
        archive.writeParentDirectories(name);
        archive.writeEntry(name, content);
    }

    private static boolean ignoredDependencyEntry(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if ("META-INF/MANIFEST.MF".equals(upper) || "MODULE-INFO.CLASS".equals(upper)) {
            return true;
        }
        if (!upper.startsWith("META-INF/")) {
            return false;
        }
        return upper.endsWith(".SF")
                || upper.endsWith(".DSA")
                || upper.endsWith(".RSA")
                || upper.endsWith(".EC")
                || "META-INF/INDEX.LIST".equals(upper);
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
