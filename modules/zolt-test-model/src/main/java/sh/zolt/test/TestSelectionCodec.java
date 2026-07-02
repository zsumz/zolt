package sh.zolt.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TestSelectionCodec {
    private TestSelectionCodec() {
    }

    public static String encodeStrings(List<String> values) {
        StringBuilder output = new StringBuilder();
        boolean first = true;
        for (String value : values == null ? List.<String>of() : values) {
            if (!first) {
                output.append(',');
            }
            first = false;
            output.append(encodeValue(value));
        }
        return output.toString();
    }

    public static List<String> decodeStrings(String label, String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String item : value.split(",", -1)) {
            if (item.isEmpty()) {
                throw new IllegalArgumentException(label + " contains an empty value.");
            }
            values.add(decodeValue(label, item));
        }
        return List.copyOf(values);
    }

    public static String encodeMethods(List<TestSelection.MethodSelector> selectors) {
        List<String> values = new ArrayList<>();
        for (TestSelection.MethodSelector selector : selectors == null ? List.<TestSelection.MethodSelector>of() : selectors) {
            values.add(selector.className() + "#" + selector.methodName());
        }
        return encodeStrings(values);
    }

    public static List<TestSelection.MethodSelector> decodeMethods(String label, String value) {
        List<String> values = decodeStrings(label, value);
        List<TestSelection.MethodSelector> selectors = new ArrayList<>();
        for (String selector : values) {
            int hash = selector.indexOf('#');
            if (hash < 1 || hash != selector.lastIndexOf('#') || hash == selector.length() - 1) {
                throw new IllegalArgumentException(label + " contains invalid method selector `" + selector + "`.");
            }
            selectors.add(new TestSelection.MethodSelector(selector.substring(0, hash), selector.substring(hash + 1)));
        }
        return List.copyOf(selectors);
    }

    private static String encodeValue(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Test selection values must be non-empty.");
        }
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '%' || character == ',') {
                output.append('%');
                output.append(String.format(Locale.ROOT, "%02X", (int) character));
            } else {
                output.append(character);
            }
        }
        return output.toString();
    }

    private static String decodeValue(String label, String value) {
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != '%') {
                output.append(character);
                continue;
            }
            if (index + 2 >= value.length()) {
                throw new IllegalArgumentException(label + " contains malformed percent encoding.");
            }
            String hex = value.substring(index + 1, index + 3);
            try {
                output.append((char) Integer.parseInt(hex, 16));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(label + " contains malformed percent encoding.", exception);
            }
            index += 2;
        }
        return output.toString();
    }
}
