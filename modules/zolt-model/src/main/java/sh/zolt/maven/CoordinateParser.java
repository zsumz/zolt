package sh.zolt.maven;

import java.util.Optional;

public final class CoordinateParser {
    public Coordinate parse(String input) {
        if (input == null || input.isBlank()) {
            throw new CoordinateParseException(
                    "Dependency coordinate is required. Use `group:artifact` or `group:artifact:version`.");
        }
        if (!input.equals(input.trim()) || containsWhitespace(input)) {
            throw new CoordinateParseException(
                    "Dependency coordinate must not contain whitespace. Use `group:artifact:version`.");
        }

        String[] parts = input.split(":", -1);
        if (parts.length != 2 && parts.length != 3) {
            throw new CoordinateParseException(
                    "Malformed dependency coordinate `"
                            + input
                            + "`. Use `group:artifact` or `group:artifact:version`.");
        }

        String groupId = requiredPart(parts[0], "group", input);
        String artifactId = requiredPart(parts[1], "artifact", input);
        Optional<String> version = parts.length == 3
                ? Optional.of(requiredPart(parts[2], "version", input))
                : Optional.empty();

        return new Coordinate(groupId, artifactId, version);
    }

    private static String requiredPart(String value, String name, String original) {
        if (value.isBlank()) {
            throw new CoordinateParseException(
                    "Malformed dependency coordinate `" + original + "`. The " + name + " segment is empty.");
        }
        return value;
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }
}
