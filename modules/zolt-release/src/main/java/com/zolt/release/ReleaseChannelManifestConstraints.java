package com.zolt.release;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Pattern;

final class ReleaseChannelManifestConstraints {
    private static final Pattern STABLE_VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z][0-9A-Za-z._-]*)?");
    private static final Pattern NIGHTLY_VERSION = Pattern.compile("[0-9A-Za-z._-]+-nightly\\.[0-9]{8}\\.[0-9A-Fa-f]{7,40}");
    private static final Pattern ARCHIVE_FILENAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern SHA256 = Pattern.compile("[0-9A-Fa-f]{64}");
    private static final Pattern SIGNATURE_KIND = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Set<String> SUPPORTED_CHANNELS = Set.of("stable", "nightly");

    private ReleaseChannelManifestConstraints() {
    }

    static void validateChannel(String channel) {
        if (!SUPPORTED_CHANNELS.contains(channel)) {
            throw new ReleaseChannelManifestException(
                    "Release channel manifest channel must be one of stable, nightly; got `" + channel + "`.");
        }
    }

    static void validateVersion(String version) {
        validateSafeSegment("version", version);
        if (STABLE_VERSION.matcher(version).matches() || NIGHTLY_VERSION.matcher(version).matches()) {
            return;
        }
        throw new ReleaseChannelManifestException(
                "Release channel manifest version must look like 0.1.0 or <base>-nightly.YYYYMMDD.<commit>; got `"
                        + version
                        + "`.");
    }

    static void validateArchiveFilename(ReleaseTarget target, String archive) {
        validateSafeSegment("archive", archive);
        if (!ARCHIVE_FILENAME.matcher(archive).matches()) {
            throw new ReleaseChannelManifestException(
                    "Release channel artifact `"
                            + target.id()
                            + "` archive must be a filename using letters, digits, dots, underscores, and hyphens.");
        }
    }

    static void validateUrl(String field, String value, boolean allowFileUrls) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new ReleaseChannelManifestException(
                    "Release channel manifest " + field + " must be a valid HTTPS URL.");
        }
        if (uri.getUserInfo() != null) {
            throw new ReleaseChannelManifestException(
                    "Release channel manifest " + field + " must not include URL credentials.");
        }
        if (allowFileUrls && "file".equalsIgnoreCase(uri.getScheme())) {
            return;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ReleaseChannelManifestException(
                    "Release channel manifest " + field + " must be a valid HTTPS URL.");
        }
    }

    static void validateSha256(ReleaseTarget target, String sha256) {
        if (!SHA256.matcher(sha256).matches()) {
            throw new ReleaseChannelManifestException(
                    "Release channel artifact `" + target.id() + "` sha256 must be exactly 64 hexadecimal characters.");
        }
    }

    static void validateSignature(ReleaseChannelArtifact.Signature signature, boolean allowFileUrls) {
        if (!SIGNATURE_KIND.matcher(signature.kind()).matches()) {
            throw new ReleaseChannelManifestException(
                    "Release channel signature kind must use letters, digits, dots, underscores, and hyphens.");
        }
        validateUrl("signature.url", signature.url(), allowFileUrls);
    }

    private static void validateSafeSegment(String field, String value) {
        if (value.isBlank()
                || !value.equals(value.strip())
                || value.contains("/")
                || value.contains("\\")
                || value.contains("..")
                || value.contains(":")
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new ReleaseChannelManifestException(
                    "Release channel manifest " + field + " must be one safe path segment.");
        }
    }
}
