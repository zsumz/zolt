package com.zolt.build;

import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
                MergeAccumulator merges = new MergeAccumulator();
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
                    entryCount += mergeRuntimeJar(archive, entries, merges, runtimeJar);
                }
                entryCount += merges.writeEntries(archive, entries);
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
            MergeAccumulator merges,
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
                String entryName = relocatedDependencyMetadataEntry(entry.getName(), runtimeJar);
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
            MergeAccumulator merges,
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

    private static final class MergeAccumulator {
        private static final String NETTY_VERSIONS = "META-INF/io.netty.versions.properties";
        private final Map<String, TreeSet<String>> serviceProviders = new TreeMap<>();
        private final Map<String, String> nettyVersions = new TreeMap<>();

        boolean accepts(String name) {
            return name.startsWith("META-INF/services/") || NETTY_VERSIONS.equals(name);
        }

        void add(String name, byte[] content, String source) {
            String text = new String(content, StandardCharsets.UTF_8);
            if (name.startsWith("META-INF/services/")) {
                serviceProviders.computeIfAbsent(name, ignored -> new TreeSet<>()).addAll(serviceProviders(text));
                return;
            }
            if (NETTY_VERSIONS.equals(name)) {
                addNettyVersions(text, source);
                return;
            }
            throw new IllegalArgumentException("Unsupported uber jar merge entry " + name);
        }

        int writeEntries(PackageArchiveWriter archive, Set<String> entries) throws IOException {
            int written = 0;
            for (Map.Entry<String, TreeSet<String>> entry : serviceProviders.entrySet()) {
                writeEntry(
                        archive,
                        entries,
                        entry.getKey(),
                        serviceContent(entry.getValue()),
                        "merged service descriptors");
                written++;
            }
            if (!nettyVersions.isEmpty()) {
                writeEntry(
                        archive,
                        entries,
                        NETTY_VERSIONS,
                        nettyVersionsContent(),
                        "merged Netty version metadata");
                written++;
            }
            return written;
        }

        private static Set<String> serviceProviders(String text) {
            TreeSet<String> providers = new TreeSet<>();
            for (String line : text.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                providers.add(trimmed);
            }
            return providers;
        }

        private void addNettyVersions(String text, String source) {
            for (String line : text.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    throw new PackageException(
                            "Could not merge Netty version metadata from "
                                    + source
                                    + ": expected key=value line but found `"
                                    + trimmed
                                    + "`.");
                }
                String key = trimmed.substring(0, separator);
                String value = trimmed.substring(separator + 1);
                String previous = nettyVersions.putIfAbsent(key, value);
                if (previous != null && !previous.equals(value)) {
                    throw new PackageException(
                            "Could not merge Netty version metadata key `"
                                    + key
                                    + "` from "
                                    + source
                                    + " because another runtime jar uses a different value.");
                }
            }
        }

        private static byte[] serviceContent(Set<String> providers) {
            return (String.join("\n", providers) + "\n").getBytes(StandardCharsets.UTF_8);
        }

        private byte[] nettyVersionsContent() {
            Map<String, String> sorted = new LinkedHashMap<>(nettyVersions);
            StringBuilder content = new StringBuilder("# Merged by Zolt uber jar packaging\n");
            for (Map.Entry<String, String> entry : sorted.entrySet()) {
                content.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            }
            return content.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
