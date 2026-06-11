package com.zolt.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PackageEvidenceManifestReader {
    public PackageEvidenceManifest read(Path manifestPath) {
        try {
            String json = Files.readString(manifestPath, StandardCharsets.UTF_8);
            return new PackageEvidenceManifest(
                    requiredString(json, "schema", manifestPath),
                    requiredString(json, "archive", manifestPath),
                    requiredString(json, "archiveSha256", manifestPath),
                    artifacts(json, manifestPath));
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not read package evidence manifest at "
                            + manifestPath
                            + ". Check that the file is readable and retry.",
                    exception);
        }
    }

    private static List<PackageEvidenceArtifact> artifacts(String json, Path manifestPath) {
        int fieldStart = json.indexOf("\"artifacts\"");
        if (fieldStart < 0) {
            return List.of();
        }
        int colon = json.indexOf(':', fieldStart);
        if (colon < 0) {
            throw malformed(manifestPath, "artifacts");
        }
        int arrayStart = nextNonWhitespace(json, colon + 1);
        if (arrayStart < 0 || json.charAt(arrayStart) != '[') {
            throw malformed(manifestPath, "artifacts");
        }
        int arrayEnd = matching(json, arrayStart, '[', ']', manifestPath);
        List<PackageEvidenceArtifact> artifacts = new ArrayList<>();
        int index = arrayStart + 1;
        while (index < arrayEnd) {
            index = nextNonWhitespace(json, index);
            if (index < 0 || index >= arrayEnd) {
                break;
            }
            char character = json.charAt(index);
            if (character == ',') {
                index++;
                continue;
            }
            if (character != '{') {
                throw malformed(manifestPath, "artifacts");
            }
            int objectEnd = matching(json, index, '{', '}', manifestPath);
            String object = json.substring(index, objectEnd + 1);
            artifacts.add(new PackageEvidenceArtifact(
                    requiredString(object, "classifier", manifestPath),
                    requiredString(object, "type", manifestPath),
                    requiredString(object, "path", manifestPath),
                    requiredInt(object, "entries", manifestPath),
                    requiredString(object, "sha256", manifestPath)));
            index = objectEnd + 1;
        }
        return List.copyOf(artifacts);
    }

    private static int requiredInt(String json, String field, Path manifestPath) {
        int fieldStart = json.indexOf("\"" + field + "\"");
        if (fieldStart < 0) {
            throw malformed(manifestPath, field);
        }
        int colon = json.indexOf(':', fieldStart);
        if (colon < 0) {
            throw malformed(manifestPath, field);
        }
        int valueStart = nextNonWhitespace(json, colon + 1);
        if (valueStart < 0) {
            throw malformed(manifestPath, field);
        }
        int valueEnd = valueStart;
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
            valueEnd++;
        }
        if (valueEnd == valueStart) {
            throw malformed(manifestPath, field);
        }
        return Integer.parseInt(json.substring(valueStart, valueEnd));
    }

    private static int matching(
            String json,
            int start,
            char open,
            char close,
            Path manifestPath) {
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int index = start; index < json.length(); index++) {
            char character = json.charAt(index);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (character == '\\') {
                escaping = inString;
                continue;
            }
            if (character == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (character == open) {
                depth++;
            } else if (character == close) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        throw malformed(manifestPath, String.valueOf(open));
    }

    private static String requiredString(String json, String field, Path manifestPath) {
        int fieldStart = json.indexOf("\"" + field + "\"");
        if (fieldStart < 0) {
            throw malformed(manifestPath, field);
        }
        int colon = json.indexOf(':', fieldStart);
        if (colon < 0) {
            throw malformed(manifestPath, field);
        }
        int quoteStart = nextNonWhitespace(json, colon + 1);
        if (quoteStart < 0 || json.charAt(quoteStart) != '"') {
            throw malformed(manifestPath, field);
        }
        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int index = quoteStart + 1; index < json.length(); index++) {
            char character = json.charAt(index);
            if (escaping) {
                value.append(unescaped(character, json, index));
                escaping = false;
                continue;
            }
            if (character == '\\') {
                escaping = true;
                continue;
            }
            if (character == '"') {
                return value.toString();
            }
            value.append(character);
        }
        throw malformed(manifestPath, field);
    }

    private static int nextNonWhitespace(String json, int start) {
        for (int index = start; index < json.length(); index++) {
            if (!Character.isWhitespace(json.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static char unescaped(char character, String json, int index) {
        return switch (character) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> throw new PackageException(
                    "Could not parse package evidence manifest string escape near character "
                            + index
                            + ". Regenerate package evidence with `zolt package`.");
        };
    }

    private static PackageException malformed(Path manifestPath, String field) {
        return new PackageException(
                "Package evidence manifest "
                        + manifestPath
                        + " is missing string field `"
                        + field
                        + "`. Regenerate package evidence with `zolt package`.");
    }
}
