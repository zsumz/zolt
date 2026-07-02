package sh.zolt.explain.gradle;

import sh.zolt.explain.MigrationExplainException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

final class GradleProperties {
    private GradleProperties() {
    }

    static Map<String, String> read(Path path) {
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        } catch (IOException exception) {
            throw new MigrationExplainException("Could not read Gradle properties for zolt explain: " + path, exception);
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            String value = properties.getProperty(name);
            if (value != null && !value.isBlank()) {
                values.put(name, value.strip());
            }
        }
        return values;
    }

    static Optional<String> value(
            String name,
            Map<String, String> projectProperties,
            Map<String, String> rootProperties) {
        String projectValue = projectProperties.get(name);
        if (projectValue != null && !projectValue.isBlank()) {
            return Optional.of(projectValue);
        }
        String rootValue = rootProperties.get(name);
        if (rootValue != null && !rootValue.isBlank()) {
            return Optional.of(rootValue);
        }
        return Optional.empty();
    }
}
