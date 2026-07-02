package sh.zolt.cli;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HelpMetavarLabelNormalizer {
    private static final Pattern ANGLE_METAVAR = Pattern.compile("<([A-Za-z][A-Za-z0-9]*)>");

    private HelpMetavarLabelNormalizer() {
    }

    static String normalize(String text) {
        if (text.isEmpty()) {
            return text;
        }

        Matcher matcher = ANGLE_METAVAR.matcher(text);
        StringBuilder normalized = new StringBuilder(text.length());
        while (matcher.find()) {
            matcher.appendReplacement(normalized, Matcher.quoteReplacement("<" + toUpperSnake(matcher.group(1)) + ">"));
        }
        matcher.appendTail(normalized);
        return normalized.toString();
    }

    private static String toUpperSnake(String value) {
        StringBuilder converted = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (index > 0 && Character.isUpperCase(current) && Character.isLowerCase(value.charAt(index - 1))) {
                converted.append('_');
            }
            converted.append(Character.toUpperCase(current));
        }
        return converted.toString().toUpperCase(Locale.ROOT);
    }
}
