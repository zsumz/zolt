package sh.zolt.dependency;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stability of a version string, decided purely from its qualifier tokens.
 *
 * <p>SNAPSHOT versions are never suggested as updates. PRERELEASE is assigned only when a KNOWN
 * negative-rank qualifier token is present ({@code alpha, a, beta, b, milestone, m, rc, cr}); every
 * other qualifier — including flavor tokens like {@code jre}, {@code android}, or calver segments —
 * is treated as RELEASE so those remain eligible update targets.
 */
public enum VersionStability {
    RELEASE,
    PRERELEASE,
    SNAPSHOT;

    private static final String SNAPSHOT_QUALIFIER = "snapshot";
    private static final Set<String> PRERELEASE_QUALIFIERS =
            Set.of("alpha", "a", "beta", "b", "milestone", "m", "rc", "cr");

    /** Classify a version string. Unknown qualifiers are RELEASE. */
    public static VersionStability of(String version) {
        boolean prerelease = false;
        for (String qualifier : qualifierTokens(version)) {
            if (qualifier.equals(SNAPSHOT_QUALIFIER)) {
                return SNAPSHOT;
            }
            if (PRERELEASE_QUALIFIERS.contains(qualifier)) {
                prerelease = true;
            }
        }
        return prerelease ? PRERELEASE : RELEASE;
    }

    /** True when a version may be suggested as an update target (everything but SNAPSHOT). */
    public boolean suggestable() {
        return this != SNAPSHOT;
    }

    private static List<String> qualifierTokens(String version) {
        List<String> qualifiers = new ArrayList<>();
        for (String segment : version.split("[.\\-_+]", -1)) {
            if (segment.isBlank()) {
                continue;
            }
            collectQualifiers(segment, qualifiers);
        }
        return qualifiers;
    }

    private static void collectQualifiers(String segment, List<String> qualifiers) {
        StringBuilder current = new StringBuilder();
        Boolean numeric = null;
        for (int index = 0; index < segment.length(); index++) {
            char character = segment.charAt(index);
            boolean characterNumeric = Character.isDigit(character);
            if (numeric != null && numeric != characterNumeric) {
                addQualifier(current, numeric, qualifiers);
                current.setLength(0);
            }
            current.append(character);
            numeric = characterNumeric;
        }
        if (!current.isEmpty()) {
            addQualifier(current, Boolean.TRUE.equals(numeric), qualifiers);
        }
    }

    private static void addQualifier(StringBuilder token, boolean numeric, List<String> qualifiers) {
        if (!numeric) {
            qualifiers.add(token.toString().toLowerCase(Locale.ROOT));
        }
    }
}
