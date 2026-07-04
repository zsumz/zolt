package sh.zolt.junit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One line of the {@link JunitWorkerProtocol}: a bare command verb followed by tab-separated
 * {@code name=value} tagged fields.
 *
 * <p>A frame is built field-by-field with {@link #put} and serialized with {@link #render}; an
 * incoming line is read with {@link #parse} and queried <em>by name</em> via {@link #require} and
 * {@link #optional}. Decoding never depends on how many fields are present or on their order, so a
 * new field is just a new key.
 *
 * <p>Names are bare ASCII tokens. Values are percent-escaped on the wire so they can never contain a
 * tab (the field separator), a newline or carriage return (the line terminator), the {@code =} that
 * separates a name from its value, or the {@code %} escape marker. Empty optional values are omitted
 * entirely rather than written as an empty field.
 */
final class Frame {
    private static final char FIELD_SEPARATOR = '\t';
    private static final char NAME_VALUE_SEPARATOR = '=';
    private static final char ESCAPE = '%';

    private final String command;
    private final Map<String, String> fields = new LinkedHashMap<>();

    private Frame(String command) {
        this.command = command;
    }

    static Frame command(String command) {
        return new Frame(command);
    }

    static Frame parse(String line) {
        if (line == null) {
            throw new IllegalArgumentException("Malformed JUnit worker frame: empty line.");
        }
        String[] rawFields = line.split(String.valueOf(FIELD_SEPARATOR), -1);
        if (rawFields[0].isBlank()) {
            throw new IllegalArgumentException("Malformed JUnit worker frame: missing command verb.");
        }
        Frame frame = new Frame(rawFields[0]);
        for (int index = 1; index < rawFields.length; index++) {
            frame.parseField(rawFields[index]);
        }
        return frame;
    }

    String command() {
        return command;
    }

    void put(String name, String value) {
        if (value == null) {
            return;
        }
        fields.put(name, value);
    }

    String require(String name, String description) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Malformed JUnit worker " + command + " frame: " + description + " (`" + name + "`) is required.");
        }
        return value;
    }

    Optional<String> optional(String name) {
        return Optional.ofNullable(fields.get(name)).filter(value -> !value.isBlank());
    }

    void rejectUnexpected(String description, List<String> allowedNames) {
        for (String name : fields.keySet()) {
            if (!allowedNames.contains(name)) {
                throw new IllegalArgumentException(
                        "Malformed " + description + ": unexpected field `" + name + "`.");
            }
        }
    }

    String render() {
        StringBuilder line = new StringBuilder(command);
        for (Map.Entry<String, String> field : fields.entrySet()) {
            line.append(FIELD_SEPARATOR)
                    .append(field.getKey())
                    .append(NAME_VALUE_SEPARATOR)
                    .append(escape(field.getValue()));
        }
        return line.toString();
    }

    private void parseField(String rawField) {
        int separator = rawField.indexOf(NAME_VALUE_SEPARATOR);
        if (separator < 1) {
            throw new IllegalArgumentException(
                    "Malformed JUnit worker " + command + " frame: field `" + rawField + "` is not name=value.");
        }
        String name = rawField.substring(0, separator);
        String value = unescape(name, rawField.substring(separator + 1));
        if (fields.put(name, value) != null) {
            throw new IllegalArgumentException(
                    "Malformed JUnit worker " + command + " frame: duplicate field `" + name + "`.");
        }
    }

    private static String escape(String value) {
        StringBuilder output = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == ESCAPE
                    || character == FIELD_SEPARATOR
                    || character == NAME_VALUE_SEPARATOR
                    || character == '\n'
                    || character == '\r') {
                output.append(ESCAPE).append(String.format("%02X", (int) character));
            } else {
                output.append(character);
            }
        }
        return output.toString();
    }

    private static String unescape(String name, String value) {
        StringBuilder output = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != ESCAPE) {
                output.append(character);
                continue;
            }
            if (index + 2 >= value.length()) {
                throw new IllegalArgumentException(
                        "JUnit worker field `" + name + "` contains malformed percent encoding.");
            }
            try {
                output.append((char) Integer.parseInt(value.substring(index + 1, index + 3), 16));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "JUnit worker field `" + name + "` contains malformed percent encoding.",
                        exception);
            }
            index += 2;
        }
        return output.toString();
    }
}
