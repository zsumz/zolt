package com.zolt.selfhost;

import com.zolt.build.PackageResult;
import com.zolt.build.PackageService;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltTomlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;

public final class SelfHostingParityService {
    private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";

    private final ZoltTomlParser tomlParser;
    private final ProjectPackager projectPackager;

    public SelfHostingParityService() {
        this(
                new ZoltTomlParser(),
                (projectDirectory, config, cacheRoot) -> new PackageService()
                        .packageJar(projectDirectory, config, cacheRoot));
    }

    SelfHostingParityService(
            ZoltTomlParser tomlParser,
            ProjectPackager projectPackager) {
        this.tomlParser = tomlParser;
        this.projectPackager = projectPackager;
    }

    public SelfHostingParityResult compare(Path projectDirectory, Path cacheRoot, Path bootstrapJar) {
        Path root = projectDirectory.toAbsolutePath().normalize();
        Path resolvedBootstrapJar = root.resolve(bootstrapJar).normalize();
        if (!Files.isRegularFile(resolvedBootstrapJar)) {
            throw new SelfHostingParityException(
                    "Self-hosting parity requires bootstrap jar at "
                            + resolvedBootstrapJar
                            + ". Build the bootstrap jar or pass --bootstrap-jar <path>.");
        }

        ProjectConfig config = tomlParser.parse(root.resolve("zolt.toml"));
        PackageResult packageResult = projectPackager.packageJar(root, config, cacheRoot);
        Path zoltJar = packageResult.jarPath();
        if (!Files.isRegularFile(zoltJar)) {
            throw new SelfHostingParityException(
                    "Self-hosting parity expected Zolt-built jar at "
                            + zoltJar
                            + ". Run zolt package and retry.");
        }

        Set<String> bootstrapEntries = comparableJarEntries(resolvedBootstrapJar);
        Set<String> zoltEntries = comparableJarEntries(zoltJar);
        Set<String> missing = new TreeSet<>(bootstrapEntries);
        missing.removeAll(zoltEntries);
        Set<String> extra = new TreeSet<>(zoltEntries);
        extra.removeAll(bootstrapEntries);
        return new SelfHostingParityResult(resolvedBootstrapJar, zoltJar, missing, extra);
    }

    private static Set<String> comparableJarEntries(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Set<String> entries = new TreeSet<>();
            zip.entries().asIterator().forEachRemaining(entry -> {
                if (!entry.isDirectory() && !MANIFEST_ENTRY.equals(entry.getName())) {
                    entries.add(entry.getName());
                }
            });
            return entries;
        } catch (IOException exception) {
            throw new SelfHostingParityException(
                    "Could not read jar entries from "
                            + jar
                            + ". Check that the jar is readable and not corrupt.",
                    exception);
        }
    }

    @FunctionalInterface
    interface ProjectPackager {
        PackageResult packageJar(Path projectDirectory, ProjectConfig config, Path cacheRoot);
    }
}
