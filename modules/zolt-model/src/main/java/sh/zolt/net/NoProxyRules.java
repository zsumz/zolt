package sh.zolt.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Host-bypass rules parsed from a {@code NO_PROXY}/{@code http.nonProxyHosts} specification.
 * Entries are comma- or pipe-separated. A {@code *} entry bypasses every host; other entries
 * match a host exactly or as a domain suffix (so {@code example.com} and {@code .example.com}
 * both match {@code example.com} and {@code repo.example.com}).
 */
final class NoProxyRules {
    private final List<String> suffixes;
    private final boolean bypassAll;

    private NoProxyRules(List<String> suffixes, boolean bypassAll) {
        this.suffixes = suffixes;
        this.bypassAll = bypassAll;
    }

    static NoProxyRules parse(String specification) {
        if (specification == null || specification.isBlank()) {
            return new NoProxyRules(List.of(), false);
        }
        List<String> suffixes = new ArrayList<>();
        boolean bypassAll = false;
        for (String rawEntry : specification.split("[,|]")) {
            String entry = normalizeEntry(rawEntry);
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.equals("*")) {
                bypassAll = true;
                continue;
            }
            suffixes.add(entry.startsWith(".") ? entry.substring(1) : entry);
        }
        return new NoProxyRules(List.copyOf(suffixes), bypassAll);
    }

    boolean matches(String host) {
        if (bypassAll) {
            return true;
        }
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        for (String suffix : suffixes) {
            if (normalizedHost.equals(suffix) || normalizedHost.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeEntry(String rawEntry) {
        String entry = rawEntry.trim().toLowerCase(Locale.ROOT);
        if (entry.startsWith("*.")) {
            entry = entry.substring(1);
        }
        int portSeparator = entry.lastIndexOf(':');
        if (portSeparator > 0 && entry.indexOf(':') == portSeparator) {
            entry = entry.substring(0, portSeparator);
        }
        return entry;
    }
}
