package com.zolt.cli;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HelpOptionValueSeparator {
    private static final Pattern ATTACHED_LONG_OPTION_VALUE =
            Pattern.compile("(^|[\\s\\[])(--[A-Za-z][A-Za-z0-9-]*)=<", Pattern.MULTILINE);

    private HelpOptionValueSeparator() {
    }

    static String useSpaceForRequiredLongOptionValues(String text) {
        if (text.isEmpty()) {
            return text;
        }

        Matcher matcher = ATTACHED_LONG_OPTION_VALUE.matcher(text);
        return matcher.replaceAll("$1$2 <");
    }
}
