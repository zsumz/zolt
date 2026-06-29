package com.zolt.release;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReleaseChannelManifestValidator {
    private static final Pattern NUMBER_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(-?\\d+)");
    private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern STABLE_VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z][0-9A-Za-z._-]*)?");
    private static final Pattern NIGHTLY_VERSION = Pattern.compile("[0-9A-Za-z._-]+-nightly\\.[0-9]{8}\\.[0-9A-Fa-f]{7,40}");
    private static final Pattern ARCHIVE_FILENAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern SHA256 = Pattern.compile("[0-9A-Fa-f]{64}");
    private static final Pattern SIGNATURE_KIND = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Set<String> SUPPORTED_CHANNELS = Set.of("stable", "nightly");
    private static final int BETA_SCHEMA_VERSION = 1;

    public ReleaseChannelManifest validate(String json) {
        if (json == null || json.isBlank()) {
            throw new ReleaseChannelManifestException("Release channel manifest is empty.");
        }
        int schemaVersion = intRequired(json, "schemaVersion", "release channel manifest");
        if (schemaVersion != BETA_SCHEMA_VERSION) {
            throw new ReleaseChannelManifestException(
                    "Release channel manifest has unsupported schemaVersion "
                            + schemaVersion
                            + "; expected "
                            + BETA_SCHEMA_VERSION
                            + ".");
        }

        String channel = stringRequired(json, "channel", "release channel manifest");
        String version = stringRequired(json, "version", "release channel manifest");
        String commit = stringRequired(json, "commit", "release channel manifest");
        String createdAt = stringRequired(json, "createdAt", "release channel manifest");
        validateChannel(channel);
        validateVersion(version);
        List<ReleaseChannelArtifact> artifacts = artifacts(json);
        return new ReleaseChannelManifest(schemaVersion, channel, version, commit, createdAt, artifacts);
    }

    private static List<ReleaseChannelArtifact> artifacts(String json) {
        String body = arrayBody(json, "artifacts")
                .orElseThrow(() -> new ReleaseChannelManifestException(
                        "Release channel manifest is missing artifacts array."));
        List<String> artifactObjects = objectBodies(body);
        if (artifactObjects.isEmpty()) {
            throw new ReleaseChannelManifestException("Release channel manifest artifacts array is empty.");
        }

        Set<ReleaseTarget> seenTargets = new HashSet<>();
        List<ReleaseChannelArtifact> artifacts = new ArrayList<>();
        for (String artifactJson : artifactObjects) {
            ReleaseChannelArtifact artifact = artifact(artifactJson);
            if (!seenTargets.add(artifact.target())) {
                throw new ReleaseChannelManifestException(
                        "Release channel manifest repeats target `" + artifact.target().id() + "`.");
            }
            artifacts.add(artifact);
        }
        return artifacts;
    }

    private static ReleaseChannelArtifact artifact(String json) {
        String targetId = stringRequired(json, "target", "release channel artifact");
        ReleaseTarget target;
        try {
            target = ReleaseTarget.fromId(targetId);
        } catch (ReleaseArchiveException exception) {
            throw new ReleaseChannelManifestException(
                    "Release channel manifest has unsupported target `"
                            + targetId
                            + "`. Supported targets: "
                            + ReleaseTarget.supportedTargets()
                            + ".");
        }

        String archive = stringRequired(json, "archive", "release channel artifact " + target.id());
        String archiveUrl = stringRequired(json, "archiveUrl", "release channel artifact " + target.id());
        Optional<String> checksumUrl = string(json, "checksumUrl");
        Optional<String> sha256 = string(json, "sha256");
        String format = stringRequired(json, "format", "release channel artifact " + target.id());
        String binaryName = stringRequired(json, "binaryName", "release channel artifact " + target.id());
        Optional<ReleaseChannelArtifact.Signature> signature = signature(json);

        validateArtifact(target, archive, archiveUrl, checksumUrl, sha256, format, binaryName, signature);
        return new ReleaseChannelArtifact(
                target,
                archive,
                archiveUrl,
                checksumUrl,
                sha256,
                format,
                binaryName,
                signature);
    }

    private static void validateArtifact(
            ReleaseTarget target,
            String archive,
            String archiveUrl,
            Optional<String> checksumUrl,
            Optional<String> sha256,
            String format,
            String binaryName,
            Optional<ReleaseChannelArtifact.Signature> signature) {
        String expectedFormat = target.archiveExtension().substring(1);
        if (!format.equals(expectedFormat)) {
            throw new ReleaseChannelManifestException(
                    "Release channel artifact `"
                            + target.id()
                            + "` has format `"
                            + format
                            + "`; expected `"
                            + expectedFormat
                            + "`.");
        }
        if (!binaryName.equals(target.binaryName())) {
            throw new ReleaseChannelManifestException(
                    "Release channel artifact `"
                            + target.id()
                            + "` has binaryName `"
                            + binaryName
                            + "`; expected `"
                            + target.binaryName()
                            + "`.");
        }
        validateArchiveFilename(target, archive);
        validateHttpsUrl("archiveUrl", archiveUrl);
        if (!archive.endsWith(target.archiveExtension()) || !archiveUrl.endsWith(target.archiveExtension())) {
            throw new ReleaseChannelManifestException(
                    "Release channel artifact `"
                            + target.id()
                            + "` must reference a native "
                            + expectedFormat
                            + " archive, not a JVM/JRE artifact.");
        }
        checksumUrl.ifPresent(value -> {
            validateHttpsUrl("checksumUrl", value);
            if (!value.endsWith(".sha256")) {
                throw new ReleaseChannelManifestException(
                        "Release channel artifact `" + target.id() + "` checksumUrl must reference a .sha256 sidecar.");
            }
        });
        sha256.ifPresent(value -> validateSha256(target, value));
        signature.ifPresent(ReleaseChannelManifestValidator::validateSignature);
        if (checksumUrl.isEmpty() && sha256.isEmpty()) {
            throw new ReleaseChannelManifestException(
                    "Release channel artifact `" + target.id() + "` must include checksumUrl or sha256.");
        }
    }

    private static Optional<ReleaseChannelArtifact.Signature> signature(String json) {
        Optional<String> body = objectBody(json, "signature");
        if (body.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ReleaseChannelArtifact.Signature(
                stringRequired(body.orElseThrow(), "kind", "release channel signature"),
                stringRequired(body.orElseThrow(), "url", "release channel signature")));
    }

    private static void validateChannel(String channel) {
        if (!SUPPORTED_CHANNELS.contains(channel)) {
            throw new ReleaseChannelManifestException(
                    "Release channel manifest channel must be one of stable, nightly; got `" + channel + "`.");
        }
    }

    private static void validateVersion(String version) {
        validateSafeSegment("version", version);
        if (STABLE_VERSION.matcher(version).matches() || NIGHTLY_VERSION.matcher(version).matches()) {
            return;
        }
        throw new ReleaseChannelManifestException(
                "Release channel manifest version must look like 0.1.0 or <base>-nightly.YYYYMMDD.<commit>; got `"
                        + version
                        + "`.");
    }

    private static void validateArchiveFilename(ReleaseTarget target, String archive) {
        validateSafeSegment("archive", archive);
        if (!ARCHIVE_FILENAME.matcher(archive).matches()) {
            throw new ReleaseChannelManifestException(
                    "Release channel artifact `"
                            + target.id()
                            + "` archive must be a filename using letters, digits, dots, underscores, and hyphens.");
        }
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

    private static void validateHttpsUrl(String field, String value) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new ReleaseChannelManifestException(
                    "Release channel manifest " + field + " must be a valid HTTPS URL.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ReleaseChannelManifestException(
                    "Release channel manifest " + field + " must be a valid HTTPS URL.");
        }
        if (uri.getUserInfo() != null) {
            throw new ReleaseChannelManifestException(
                    "Release channel manifest " + field + " must not include URL credentials.");
        }
    }

    private static void validateSha256(ReleaseTarget target, String sha256) {
        if (!SHA256.matcher(sha256).matches()) {
            throw new ReleaseChannelManifestException(
                    "Release channel artifact `" + target.id() + "` sha256 must be exactly 64 hexadecimal characters.");
        }
    }

    private static void validateSignature(ReleaseChannelArtifact.Signature signature) {
        if (!SIGNATURE_KIND.matcher(signature.kind()).matches()) {
            throw new ReleaseChannelManifestException(
                    "Release channel signature kind must use letters, digits, dots, underscores, and hyphens.");
        }
        validateHttpsUrl("signature.url", signature.url());
    }

    private static int intRequired(String json, String fieldName, String context) {
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

    private static String stringRequired(String json, String fieldName, String context) {
        return string(json, fieldName)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new ReleaseChannelManifestException(context + " is missing `" + fieldName + "`."));
    }

    private static Optional<String> string(String json, String fieldName) {
        Matcher matcher = Pattern.compile(String.format(STRING_FIELD.pattern(), Pattern.quote(fieldName)), Pattern.DOTALL)
                .matcher(json);
        return matcher.find() ? Optional.of(unescape(matcher.group(1))) : Optional.empty();
    }

    private static Optional<String> arrayBody(String json, String fieldName) {
        return enclosedBody(json, fieldName, '[', ']');
    }

    private static Optional<String> objectBody(String json, String fieldName) {
        return enclosedBody(json, fieldName, '{', '}');
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

    private static List<String> objectBodies(String arrayBody) {
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
