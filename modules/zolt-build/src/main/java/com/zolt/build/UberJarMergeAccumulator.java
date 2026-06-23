package com.zolt.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class UberJarMergeAccumulator {
    private static final String NETTY_VERSIONS = "META-INF/io.netty.versions.properties";

    private final Map<String, TreeSet<String>> serviceProviders = new TreeMap<>();
    private final Map<String, TreeSet<String>> serviceSources = new TreeMap<>();
    private final Map<String, String> nettyVersions = new TreeMap<>();
    private final TreeSet<String> nettySources = new TreeSet<>();

    boolean accepts(String name) {
        return name.startsWith("META-INF/services/") || NETTY_VERSIONS.equals(name);
    }

    void add(String name, byte[] content, String source) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (name.startsWith("META-INF/services/")) {
            serviceProviders.computeIfAbsent(name, ignored -> new TreeSet<>()).addAll(serviceProviders(text));
            serviceSources.computeIfAbsent(name, ignored -> new TreeSet<>()).add(source);
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
            writeEntry(archive, entries, entry.getKey(), serviceContent(entry.getValue()), "merged service descriptors");
            written++;
        }
        if (!nettyVersions.isEmpty()) {
            writeEntry(archive, entries, NETTY_VERSIONS, nettyVersionsContent(), "merged Netty version metadata");
            written++;
        }
        return written;
    }

    List<PackageMergeDecision> decisions() {
        List<PackageMergeDecision> decisions = new ArrayList<>();
        serviceSources.forEach((path, sources) -> decisions.add(new PackageMergeDecision(
                "service-descriptor",
                path,
                java.util.Optional.empty(),
                List.copyOf(sources))));
        if (!nettySources.isEmpty()) {
            decisions.add(new PackageMergeDecision(
                    "netty-version-metadata",
                    NETTY_VERSIONS,
                    java.util.Optional.empty(),
                    List.copyOf(nettySources)));
        }
        return decisions;
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
        nettySources.add(source);
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

    private static void writeEntry(
            PackageArchiveWriter archive,
            Set<String> entries,
            String name,
            byte[] content,
            String source) throws IOException {
        if (!entries.add(name)) {
            throw new PackageException("Duplicate uber jar entry `" + name + "` while merging " + source + ".");
        }
        archive.writeParentDirectories(name);
        archive.writeEntry(name, content);
    }
}
