package sh.zolt.release.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReleaseJsonFields {
    private static final Pattern NUMBER_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(-?\\d+)");
    private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private ReleaseJsonFields() {
    }

    static int intRequired(String json, String fieldName, String context) {
        Matcher matcher = Pattern.compile(String.format(NUMBER_FIELD.pattern(), Pattern.quote(fieldName))).matcher(json);
        if (!matcher.find()) {
            throw new ReleaseChannelManifestException(context + " is missing `" + fieldName + "`.");
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new ReleaseChannelManifestException(context + " has invalid `" + fieldName + "`.");
        }
    }

    static String stringRequired(String json, String fieldName, String context) {
        return string(json, fieldName)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new ReleaseChannelManifestException(context + " is missing `" + fieldName + "`."));
    }

    static Optional<String> string(String json, String fieldName) {
        Matcher matcher = Pattern.compile(String.format(STRING_FIELD.pattern(), Pattern.quote(fieldName)), Pattern.DOTALL)
                .matcher(json);
        return matcher.find() ? Optional.of(unescape(matcher.group(1))) : Optional.empty();
    }

    static Optional<String> arrayBody(String json, String fieldName) {
        return enclosedBody(json, fieldName, '[', ']');
    }

    static Optional<String> objectBody(String json, String fieldName) {
        return enclosedBody(json, fieldName, '{', '}');
    }

    static List<String> objectBodies(String arrayBody) {
        List<String> objects = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int open = -1;
        for (int index = 0; index < arrayBody.length(); index++) {
            char ch = arrayBody.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                if (depth == 0) {
                    open = index;
                }
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && open >= 0) {
                    objects.add(arrayBody.substring(open, index + 1));
                }
            }
        }
        return objects;
    }

    private static Optional<String> enclosedBody(String json, String fieldName, char openChar, char closeChar) {
        int field = json.indexOf("\"" + fieldName + "\"");
        if (field < 0) {
            return Optional.empty();
        }
        int open = json.indexOf(openChar, field);
        if (open < 0) {
            return Optional.empty();
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = open; index < json.length(); index++) {
            char ch = json.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == openChar) {
                depth++;
            } else if (ch == closeChar) {
                depth--;
                if (depth == 0) {
                    return Optional.of(json.substring(open + 1, index));
                }
            }
        }
        return Optional.empty();
    }

    private static String unescape(String value) {
        StringBuilder text = new StringBuilder();
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!escaped && ch == '\\') {
                escaped = true;
                continue;
            }
            if (escaped) {
                text.append(switch (ch) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '/' -> '/';
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> ch;
                });
                escaped = false;
            } else {
                text.append(ch);
            }
        }
        if (escaped) {
            text.append('\\');
        }
        return text.toString();
    }
}
