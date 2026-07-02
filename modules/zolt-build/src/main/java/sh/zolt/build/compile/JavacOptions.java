package sh.zolt.build.compile;

import sh.zolt.build.JavacException;
import java.util.List;

public record JavacOptions(
        String release,
        String encoding,
        List<String> arguments) {
    public JavacOptions {
        release = normalize(release);
        encoding = normalize(encoding);
        arguments = copyArguments(arguments);
    }

    public static JavacOptions empty() {
        return new JavacOptions("", "", List.of());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    private static List<String> copyArguments(List<String> values) {
        if (values == null) {
            return List.of();
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new JavacException("Invalid compiler argument. Use non-empty strings in [compiler].args and [compiler].testArgs.");
            }
        }
        return List.copyOf(values);
    }
}
