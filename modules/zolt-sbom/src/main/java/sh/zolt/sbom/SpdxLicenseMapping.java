package sh.zolt.sbom;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Curated normalization of raw Maven license names and URLs to SPDX identifiers. Keyed on the
 * lowercased license name AND on the normalized URL — a POM that declares only a URL still resolves.
 *
 * <p>The mapping is intentionally conservative: a declared license that matches no curated spelling
 * stays {@code UNMAPPED} (raw name/url preserved) and is never guessed into a nearby identifier.
 */
public final class SpdxLicenseMapping {
    private final Map<String, String> byName = new HashMap<>();
    private final Map<String, String> byUrl = new HashMap<>();

    public SpdxLicenseMapping() {
        register("Apache-2.0",
                List.of(
                        "apache-2.0",
                        "apache 2.0",
                        "apache license 2.0",
                        "apache license, version 2.0",
                        "apache license version 2.0",
                        "the apache license, version 2.0",
                        "the apache software license, version 2.0",
                        "apache software license - version 2.0",
                        "asl 2.0"),
                List.of(
                        "apache.org/licenses/license-2.0",
                        "apache.org/licenses/license-2.0.txt",
                        "apache.org/licenses/license-2.0.html",
                        "opensource.org/licenses/apache-2.0"));
        register("MIT",
                List.of("mit", "mit license", "the mit license", "the mit license (mit)"),
                List.of("opensource.org/licenses/mit", "mit-license.org"));
        register("BSD-2-Clause",
                List.of("bsd-2-clause", "bsd 2-clause license", "the bsd 2-clause license", "simplified bsd license"),
                List.of("opensource.org/licenses/bsd-2-clause"));
        register("BSD-3-Clause",
                List.of(
                        "bsd-3-clause",
                        "bsd",
                        "the bsd license",
                        "bsd license",
                        "new bsd license",
                        "the new bsd license",
                        "bsd 3-clause license",
                        "the bsd 3-clause license",
                        "revised bsd license",
                        "eclipse distribution license - v 1.0",
                        "eclipse distribution license v. 1.0",
                        "eclipse distribution license (new bsd license)",
                        "edl 1.0",
                        "edl-1.0"),
                List.of(
                        "opensource.org/licenses/bsd-3-clause",
                        "eclipse.org/org/documents/edl-v10.php",
                        "eclipse.org/org/documents/edl-v10.html"));
        register("EPL-1.0",
                List.of(
                        "epl-1.0",
                        "epl 1.0",
                        "eclipse public license 1.0",
                        "eclipse public license - v 1.0",
                        "eclipse public license, version 1.0"),
                List.of("eclipse.org/legal/epl-v10.html"));
        register("EPL-2.0",
                List.of(
                        "epl-2.0",
                        "epl 2.0",
                        "eclipse public license 2.0",
                        "eclipse public license - v 2.0",
                        "eclipse public license v2.0",
                        "eclipse public license, version 2.0"),
                List.of("eclipse.org/legal/epl-2.0", "eclipse.org/legal/epl-2.0/"));
        register("LGPL-2.1-only",
                List.of(
                        "lgpl-2.1",
                        "lgpl 2.1",
                        "gnu lesser general public license version 2.1",
                        "gnu lesser general public license, version 2.1"),
                List.of("gnu.org/licenses/old-licenses/lgpl-2.1.html"));
        register("LGPL-3.0-only",
                List.of(
                        "lgpl-3.0",
                        "lgpl 3.0",
                        "gnu lesser general public license version 3.0",
                        "gnu lesser general public license, version 3"),
                List.of("gnu.org/licenses/lgpl-3.0.html", "gnu.org/licenses/lgpl.html"));
        register("GPL-2.0-only",
                List.of("gpl-2.0", "gpl 2.0", "gnu general public license, version 2"),
                List.of("gnu.org/licenses/old-licenses/gpl-2.0.html"));
        register("GPL-2.0-with-classpath-exception",
                List.of(
                        "gpl-2.0-with-classpath-exception",
                        "gpl2 w/ cpe",
                        "gnu general public license, version 2 with the classpath exception",
                        "gnu general public license, version 2 with the gnu classpath exception"),
                List.of("openjdk.java.net/legal/gplv2+ce.html", "openjdk.org/legal/gplv2+ce.html"));
        register("CDDL-1.0",
                List.of(
                        "cddl-1.0",
                        "cddl 1.0",
                        "common development and distribution license",
                        "common development and distribution license 1.0",
                        "common development and distribution license (cddl) v1.0"),
                List.of("glassfish.dev.java.net/public/cddlv1.0.html", "opensource.org/licenses/cddl-1.0"));
        register("CDDL-1.1",
                List.of("cddl-1.1", "cddl 1.1", "common development and distribution license 1.1"),
                List.of("glassfish.java.net/public/cddl+gpl_1_1.html"));
        register("MPL-2.0",
                List.of(
                        "mpl-2.0",
                        "mpl 2.0",
                        "mozilla public license 2.0",
                        "mozilla public license version 2.0",
                        "mozilla public license, version 2.0"),
                List.of("mozilla.org/mpl/2.0", "mozilla.org/mpl/2.0/"));
        register("ISC",
                List.of("isc", "isc license"),
                List.of("opensource.org/licenses/isc"));
        register("Unlicense",
                List.of("unlicense", "the unlicense"),
                List.of("unlicense.org"));
    }

    /** Returns the SPDX id for a declared name/url, or empty when nothing in the curated set matches. */
    public Optional<String> spdxId(Optional<String> name, Optional<String> url) {
        Optional<String> byNameMatch = name
                .map(SpdxLicenseMapping::normalizeName)
                .map(byName::get);
        if (byNameMatch.isPresent()) {
            return byNameMatch;
        }
        return url
                .map(SpdxLicenseMapping::normalizeUrl)
                .map(byUrl::get);
    }

    private void register(String spdxId, List<String> names, List<String> urls) {
        for (String name : names) {
            byName.put(normalizeName(name), spdxId);
        }
        for (String url : urls) {
            byUrl.put(normalizeUrl(url), spdxId);
        }
    }

    private static String normalizeName(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String normalizeUrl(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceFirst("^https?://", "");
        normalized = normalized.replaceFirst("^www\\.", "");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
