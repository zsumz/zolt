package sh.zolt.update;

import java.util.List;

/** Matches a surface against report/update selectors: a coordinate, alias, or section token. */
final class Selectors {
    private Selectors() {
    }

    static boolean matches(String identifier, String section, String surfaceJsonName, List<String> selectors) {
        if (selectors.isEmpty()) {
            return true;
        }
        String token = section.replace("[", "").replace("]", "");
        return selectors.stream().anyMatch(selector -> identifier.equals(selector)
                || surfaceJsonName.equals(selector)
                || token.equals(selector)
                || token.startsWith(selector + "."));
    }
}
