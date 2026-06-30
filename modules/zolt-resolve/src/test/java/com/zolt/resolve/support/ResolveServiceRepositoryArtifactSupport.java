package com.zolt.resolve.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public abstract class ResolveServiceRepositoryArtifactSupport {
    protected void addArtifact(
            Map<String, byte[]> responses,
            String groupId,
            String artifactId,
            String version,
            String pom) {
        addArtifact(responses, groupId, artifactId, version, pom, Map.of());
    }

    protected void addArtifact(
            Map<String, byte[]> responses,
            String groupId,
            String artifactId,
            String version,
            String pom,
            Map<String, String> jarEntries) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + ".pom", pom.getBytes(StandardCharsets.UTF_8));
        responses.put(base + ".jar", jarBytes(jarEntries));
    }

    protected void addClassifierJar(
            Map<String, byte[]> responses,
            String groupId,
            String artifactId,
            String version,
            String classifier,
            Map<String, String> jarEntries) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + "-" + classifier + ".jar", jarBytes(jarEntries));
    }

    protected void addPom(Map<String, byte[]> responses, String groupId, String artifactId, String version, String pom) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + ".pom", pom.getBytes(StandardCharsets.UTF_8));
    }

    protected void writeLocalArtifact(
            Path root,
            String groupId,
            String artifactId,
            String version,
            String pom,
            Map<String, String> jarEntries) {
        String base = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        writeFile(root.resolve(base + ".pom"), pom.getBytes(StandardCharsets.UTF_8));
        writeFile(root.resolve(base + ".jar"), jarBytes(jarEntries));
    }

    protected static void writeFile(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new AssertionError("Could not write test file " + path, exception);
        }
    }

    protected static String simplePom(String groupId, String artifactId, String version) {
        return """
                <project>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(groupId, artifactId, version);
    }

    protected static String jarRepositoryPath(String groupId, String artifactId, String version) {
        return "/maven2/"
                + groupId.replace('.', '/')
                + "/"
                + artifactId
                + "/"
                + version
                + "/"
                + artifactId
                + "-"
                + version
                + ".jar";
    }

    protected static String pomRepositoryPath(String groupId, String artifactId, String version) {
        return "/maven2/"
                + groupId.replace('.', '/')
                + "/"
                + artifactId
                + "/"
                + version
                + "/"
                + artifactId
                + "-"
                + version
                + ".pom";
    }

    protected static byte[] jarBytes(Map<String, String> entries) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (JarOutputStream jar = new JarOutputStream(bytes)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    jar.putNextEntry(new JarEntry(entry.getKey()));
                    jar.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    jar.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("Could not create test jar bytes.", exception);
        }
    }
}
