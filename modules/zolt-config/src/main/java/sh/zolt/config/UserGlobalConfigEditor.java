package sh.zolt.config;

import sh.zolt.project.toolchain.JavaFeature;
import sh.zolt.project.toolchain.JavaToolchainRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class UserGlobalConfigEditor {
    private final UserGlobalConfigParser parser;

    public UserGlobalConfigEditor() {
        this(new UserGlobalConfigParser());
    }

    UserGlobalConfigEditor(UserGlobalConfigParser parser) {
        this.parser = parser;
    }

    public void setJavaToolchainDefault(Path configPath, JavaToolchainRequest request) {
        Path normalized = configPath.toAbsolutePath().normalize();
        String content = existingOrDefault(normalized);
        parser.parse(content, normalized);
        write(normalized, appendJavaDefault(removeJavaDefault(content), request));
    }

    public void unsetJavaToolchainDefault(Path configPath) {
        Path normalized = configPath.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            return;
        }
        String content = read(normalized);
        parser.parse(content, normalized);
        write(normalized, removeJavaDefault(content));
    }

    private static String existingOrDefault(Path configPath) {
        if (!Files.exists(configPath)) {
            return "version = 1\n";
        }
        return read(configPath);
    }

    private static String read(Path configPath) {
        try {
            return Files.readString(configPath);
        } catch (IOException exception) {
            throw new UserGlobalConfigException(
                    "Could not read user global config at " + configPath + ". Check that the file is readable.",
                    exception);
        }
    }

    private static void write(Path configPath, String content) {
        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(configPath, content);
        } catch (IOException exception) {
            throw new UserGlobalConfigException(
                    "Could not write user global config at " + configPath + ". Check that the file is writable.",
                    exception);
        }
    }

    private static String removeJavaDefault(String content) {
        StringBuilder output = new StringBuilder();
        boolean skipping = false;
        for (String line : content.lines().toList()) {
            String trimmed = line.strip();
            if ("[defaults.toolchain.java]".equals(trimmed)) {
                skipping = true;
                continue;
            }
            if (skipping && trimmed.startsWith("[")) {
                skipping = false;
            }
            if (!skipping) {
                output.append(line).append('\n');
            }
        }
        return output.toString().stripTrailing() + "\n";
    }

    private static String appendJavaDefault(String content, JavaToolchainRequest request) {
        StringBuilder output = new StringBuilder(content.stripTrailing());
        output.append("\n\n[defaults.toolchain.java]\n");
        assignment(output, "version", request.version());
        assignment(output, "distribution", request.distribution().orElseThrow().id());
        output.append("features = ");
        stringArray(output, request.features().stream().map(JavaFeature::id).sorted().toList());
        output.append('\n');
        assignment(output, "policy", request.policy().id());
        return output.toString();
    }

    private static void assignment(StringBuilder output, String key, String value) {
        output.append(key).append(" = ").append(quote(value)).append('\n');
    }

    private static void stringArray(StringBuilder output, List<String> values) {
        output.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                output.append(", ");
            }
            output.append(quote(values.get(index)));
        }
        output.append(']');
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
