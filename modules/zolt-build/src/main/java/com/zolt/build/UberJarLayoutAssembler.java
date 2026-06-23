package com.zolt.build;

import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
            List<PackageMergeDecision> mergeDecisions = new ArrayList<>();
            try (PackageArchiveWriter archive = PackageArchiveWriter.open(jarPath)) {
                UberJarMergeAccumulator merges = new UberJarMergeAccumulator();
                writeEntry(archive, entries, manifest.path(), manifest.content(), "generated manifest");
                entryCount++;
                for (Path file : compiledFiles(outputDirectory)) {
                    if (writeOrCollectEntry(
                            archive,
                            entries,
                            merges,
                            entryName(outputDirectory, file),
                            Files.readAllBytes(file),
                            "application output")) {
                        entryCount++;
                    }
                }
                for (PackageRuntimeJar runtimeJar : runtimeJars) {
                    entryCount += mergeRuntimeJar(archive, entries, merges, mergeDecisions, runtimeJar);
                }
                entryCount += merges.writeEntries(archive, entries);
                mergeDecisions.addAll(merges.decisions());
            }
            return new PackageResult(
                    buildResult,
                    PackageMode.UBER,
                    jarPath,
                    java.util.Optional.empty(),
                    entryCount - 1,
                    manifest.mainClass().isPresent())
                    .withMergeDecisions(mergeDecisions);
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
            UberJarMergeAccumulator merges,
            List<PackageMergeDecision> mergeDecisions,
            PackageRuntimeJar runtimeJar) throws IOException {
        int merged = 0;
        try (JarFile jar = new JarFile(runtimeJar.jarPath().toFile())) {
            List<java.util.jar.JarEntry> jarEntries = jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .sorted(Comparator.comparing(java.util.jar.JarEntry::getName))
                    .toList();
            for (java.util.jar.JarEntry entry : jarEntries) {
                if (ignoredDependencyEntry(entry.getName())) {
                    if (isDependencyModuleDescriptor(entry.getName())) {
                        mergeDecisions.add(new PackageMergeDecision(
                                "omitted-module-descriptor",
                                entry.getName(),
                                java.util.Optional.empty(),
                                List.of(runtimeJar.packageId().toString())));
                    }
                    continue;
                }
                byte[] content;
                try (InputStream input = jar.getInputStream(entry)) {
                    content = input.readAllBytes();
                }
                String originalEntryName = entry.getName();
                String entryName = relocatedDependencyMetadataEntry(originalEntryName, runtimeJar);
                if (!entryName.equals(originalEntryName)) {
                    mergeDecisions.add(new PackageMergeDecision(
                            "relocated-metadata",
                            originalEntryName,
                            java.util.Optional.of(entryName),
                            List.of(runtimeJar.packageId().toString())));
                }
                if (writeOrCollectEntry(
                        archive,
                        entries,
                        merges,
                        entryName,
                        content,
                        runtimeJar.packageId().toString())) {
                    merged++;
                }
            }
        }
        return merged;
    }

    private static boolean writeOrCollectEntry(
            PackageArchiveWriter archive,
            Set<String> entries,
            UberJarMergeAccumulator merges,
            String name,
            byte[] content,
            String source) throws IOException {
        if (merges.accepts(name)) {
            if (entries.contains(name)) {
                throw duplicateEntry(name, source);
            }
            merges.add(name, content, source);
            return false;
        }
        writeEntry(archive, entries, name, content, source);
        return true;
    }

    private static void writeEntry(
            PackageArchiveWriter archive,
            Set<String> entries,
            String name,
            byte[] content,
            String source) throws IOException {
        if (!entries.add(name)) {
            throw duplicateEntry(name, source);
        }
        archive.writeParentDirectories(name);
        archive.writeEntry(name, content);
    }

    private static PackageException duplicateEntry(String name, String source) {
        return new PackageException(
                "Duplicate uber jar entry `"
                        + name
                        + "` while merging "
                        + source
                        + ". Move one dependency out of the runtime classpath or use `thin` package mode.");
    }

    private static boolean ignoredDependencyEntry(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if ("META-INF/MANIFEST.MF".equals(upper)
                || "MODULE-INFO.CLASS".equals(upper)
                || upper.matches("META-INF/VERSIONS/[0-9]+/MODULE-INFO\\.CLASS")) {
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

    private static boolean isDependencyModuleDescriptor(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return "MODULE-INFO.CLASS".equals(upper)
                || upper.matches("META-INF/VERSIONS/[0-9]+/MODULE-INFO\\.CLASS");
    }

    private static String relocatedDependencyMetadataEntry(String name, PackageRuntimeJar runtimeJar) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("META-INF/")) {
            return name;
        }
        String fileName = name.substring(name.lastIndexOf('/') + 1);
        String upperFileName = fileName.toUpperCase(Locale.ROOT);
        if (!(upperFileName.startsWith("LICENSE")
                || upperFileName.startsWith("NOTICE")
                || upperFileName.startsWith("DEPENDENCIES"))) {
            return name;
        }
        return "META-INF/zolt-uber/"
                + runtimeJar.packageId().groupId().replace('.', '/')
                + "/"
                + runtimeJar.packageId().artifactId()
                + "/"
                + runtimeJar.version()
                + "/"
                + fileName;
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
