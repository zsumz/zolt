package sh.zolt.project.toolchain;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public enum JavaFeature {
    NATIVE_IMAGE("native-image");

    private final String id;

    JavaFeature(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<JavaFeature> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String normalized = id.strip();
        return Arrays.stream(values())
                .filter(feature -> feature.id.equals(normalized))
                .findFirst();
    }

    public static String supportedIds() {
        return Arrays.stream(values())
                .map(JavaFeature::id)
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
