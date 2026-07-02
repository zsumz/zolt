package sh.zolt.resolve;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

abstract class ResolveServiceRepositoryArtifactSupport {
    void addArtifact(
            Map<String, byte[]> responses,
            String groupId,
            String artifactId,
            String version,
            String pom) {
        addArtifact(responses, groupId, artifactId, version, pom, Map.of());
    }

    void addArtifact(
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

    void addClassifierJar(
            Map<String, byte[]> responses,
            String groupId,
            String artifactId,
            String version,
            String classifier,
            Map<String, String> jarEntries) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + "-" + classifier + ".jar", jarBytes(jarEntries));
    }

    void addPom(Map<String, byte[]> responses, String groupId, String artifactId, String version, String pom) {
        String base = "/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        responses.put(base + ".pom", pom.getBytes(StandardCharsets.UTF_8));
    }

    void writeLocalArtifact(
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

    static void writeFile(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new AssertionError("Could not write test file " + path, exception);
        }
    }

    static String simplePom(String groupId, String artifactId, String version) {
        return """
                <project>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(groupId, artifactId, version);
    }

    static String jarRepositoryPath(String groupId, String artifactId, String version) {
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

    static String pomRepositoryPath(String groupId, String artifactId, String version) {
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

    static byte[] jarBytes(Map<String, String> entries) {
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
