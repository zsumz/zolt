package sh.zolt.maven.repository;

import java.util.Optional;

/**
 * A raw Maven {@code <license>} entry, parsed verbatim from a POM. All fields are optional because a
 * POM may declare a license by name only, by URL only, or with distribution/comments. Normalization
 * to SPDX identifiers happens downstream in the SBOM/license engine — this record keeps the source
 * spellings untouched.
 */
public record RawPomLicense(
        Optional<String> name,
        Optional<String> url,
        Optional<String> distribution,
        Optional<String> comments) {
    public RawPomLicense {
        name = name == null ? Optional.empty() : name;
        url = url == null ? Optional.empty() : url;
        distribution = distribution == null ? Optional.empty() : distribution;
        comments = comments == null ? Optional.empty() : comments;
    }
}
