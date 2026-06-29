package com.zolt.release;

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
    private static final int BETA_SCHEMA_VERSION = 1;

    public ReleaseChannelManifest validate(String json) {
        return validate(json, false);
    }

    ReleaseChannelManifest validateLocalManifest(String json) {
        return validate(json, true);
    }

    private ReleaseChannelManifest validate(String json, boolean allowFileUrls) {
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
        ReleaseChannelManifestConstraints.validateChannel(channel);
        ReleaseChannelManifestConstraints.validateVersion(version);
        List<ReleaseChannelArtifact> artifacts = artifacts(json, allowFileUrls);
        return new ReleaseChannelManifest(schemaVersion, channel, version, commit, createdAt, artifacts);
    }

    private static List<ReleaseChannelArtifact> artifacts(String json, boolean allowFileUrls) {
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
            ReleaseChannelArtifact artifact = artifact(artifactJson, allowFileUrls);
            if (!seenTargets.add(artifact.target())) {
                throw new ReleaseChannelManifestException(
                        "Release channel manifest repeats target `" + artifact.target().id() + "`.");
            }
            artifacts.add(artifact);
        }
        return artifacts;
    }

    private static ReleaseChannelArtifact artifact(String json, boolean allowFileUrls) {
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

        validateArtifact(target, archive, archiveUrl, checksumUrl, sha256, format, binaryName, signature, allowFileUrls);
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
            Optional<ReleaseChannelArtifact.Signature> signature,
            boolean allowFileUrls) {
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
        ReleaseChannelManifestConstraints.validateArchiveFilename(target, archive);
        ReleaseChannelManifestConstraints.validateUrl("archiveUrl", archiveUrl, allowFileUrls);
        if (!archive.endsWith(target.archiveExtension()) || !archiveUrl.endsWith(target.archiveExtension())) {
            throw new ReleaseChannelManifestException(
                    "Release channel artifact `"
                            + target.id()
                            + "` must reference a native "
                            + expectedFormat
                            + " archive, not a JVM/JRE artifact.");
        }
        checksumUrl.ifPresent(value -> {
            ReleaseChannelManifestConstraints.validateUrl("checksumUrl", value, allowFileUrls);
            if (!value.endsWith(".sha256")) {
                throw new ReleaseChannelManifestException(
                        "Release channel artifact `" + target.id() + "` checksumUrl must reference a .sha256 sidecar.");
            }
        });
        sha256.ifPresent(value -> ReleaseChannelManifestConstraints.validateSha256(target, value));
        signature.ifPresent(value -> ReleaseChannelManifestConstraints.validateSignature(value, allowFileUrls));
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
