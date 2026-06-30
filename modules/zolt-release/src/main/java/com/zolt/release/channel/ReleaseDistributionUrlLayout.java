package com.zolt.release.channel;

import java.util.Objects;

public final class ReleaseDistributionUrlLayout {
    public static final String DEFAULT_ORIGIN = "https://dist.zolt.build";

    private final String origin;

    public ReleaseDistributionUrlLayout() {
        this(DEFAULT_ORIGIN);
    }

    public ReleaseDistributionUrlLayout(String origin) {
        String normalized = Objects.requireNonNull(origin, "origin").strip();
        if (!normalized.startsWith("https://")) {
            throw new ReleaseChannelManifestException("Release distribution origin must use HTTPS.");
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.origin = normalized;
    }

    public String origin() {
        return origin;
    }

    public String installScriptUrl() {
        return origin + "/install.sh";
    }

    public String channelManifestUrl(String channel) {
        return origin + "/channels/" + safePathSegment(channel, "channel") + ".json";
    }

    public String archiveUrl(String channel, String version, String archive) {
        return artifactBase(channel, version) + "/" + safePathSegment(archive, "archive");
    }

    public String checksumUrl(String channel, String version, String archive) {
        return archiveUrl(channel, version, archive) + ".sha256";
    }

    public String signatureUrl(String channel, String version, String archive, String extension) {
        return archiveUrl(channel, version, archive) + safeSignatureExtension(extension);
    }

    private String artifactBase(String channel, String version) {
        return origin + "/artifacts/" + safePathSegment(channel, "channel") + "/" + safePathSegment(version, "version");
    }

    private static String safePathSegment(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()
                || normalized.contains("/")
                || normalized.contains("\\")
                || normalized.contains("..")) {
            throw new ReleaseChannelManifestException("Release distribution " + label + " must be one URL path segment.");
        }
        return normalized;
    }

    private static String safeSignatureExtension(String extension) {
        String normalized = Objects.requireNonNull(extension, "extension").strip();
        if (normalized.isEmpty() || !normalized.startsWith(".") || normalized.contains("/") || normalized.contains("\\")) {
            throw new ReleaseChannelManifestException("Release distribution signature extension must start with `.` and stay within one URL path segment.");
        }
        return normalized;
    }
}
