package com.zolt.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PackageEvidenceManifestReader {
    public PackageEvidenceManifest read(Path manifestPath) {
        try {
            String json = Files.readString(manifestPath, StandardCharsets.UTF_8);
            return new PackageEvidenceManifest(
                    requiredString(json, "schema", manifestPath),
                    requiredString(json, "archive", manifestPath),
                    requiredString(json, "archiveSha256", manifestPath),
                    artifacts(json, manifestPath),
                    uberMergeDecisions(json, manifestPath));
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not read package evidence manifest at "
                            + manifestPath
                            + ". Check that the file is readable and retry.",
                    exception);
        }
    }

    private static List<PackageMergeDecision> uberMergeDecisions(String json, Path manifestPath) {
        List<String> objects = objectArray(json, "uberMergeDecisions", manifestPath);
        if (objects.isEmpty()) {
            return List.of();
        }
        List<PackageMergeDecision> decisions = new ArrayList<>();
        for (String object : objects) {
            decisions.add(new PackageMergeDecision(
                    requiredString(object, "kind", manifestPath),
                    requiredString(object, "path", manifestPath),
                    nullableString(object, "target", manifestPath),
                    stringArray(object, "sources", manifestPath)));
        }
        return List.copyOf(decisions);
    }

    private static List<PackageEvidenceArtifact> artifacts(String json, Path manifestPath) {
        List<String> objects = objectArray(json, "artifacts", manifestPath);
        if (objects.isEmpty()) {
            return List.of();
        }
        List<PackageEvidenceArtifact> artifacts = new ArrayList<>();
        for (String object : objects) {
            artifacts.add(new PackageEvidenceArtifact(
                    requiredString(object, "classifier", manifestPath),
                    requiredString(object, "type", manifestPath),
                    requiredString(object, "path", manifestPath),
                    requiredInt(object, "entries", manifestPath),
                    requiredString(object, "sha256", manifestPath)));
        }
        return List.copyOf(artifacts);
    }

    private static List<String> objectArray(String json, String field, Path manifestPath) {
        int fieldStart = json.indexOf("\"" + field + "\"");
        if (fieldStart < 0) {
            return List.of();
        }
        int colon = json.indexOf(':', fieldStart);
        if (colon < 0) {
            throw malformed(manifestPath, field);
        }
        int arrayStart = nextNonWhitespace(json, colon + 1);
        if (arrayStart < 0 || json.charAt(arrayStart) != '[') {
            throw malformed(manifestPath, field);
        }
        int arrayEnd = matching(json, arrayStart, '[', ']', manifestPath);
        List<String> objects = new ArrayList<>();
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
                throw malformed(manifestPath, field);
            }
            int objectEnd = matching(json, index, '{', '}', manifestPath);
            objects.add(json.substring(index, objectEnd + 1));
            index = objectEnd + 1;
        }
        return objects;
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
        return stringValue(json, quoteStart, manifestPath).value();
    }

    private static Optional<String> nullableString(String json, String field, Path manifestPath) {
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
        if (json.startsWith("null", valueStart)) {
            return Optional.empty();
        }
        return Optional.of(requiredString(json, field, manifestPath));
    }

    private static List<String> stringArray(String json, String field, Path manifestPath) {
        int fieldStart = json.indexOf("\"" + field + "\"");
        if (fieldStart < 0) {
            throw malformed(manifestPath, field);
        }
        int colon = json.indexOf(':', fieldStart);
        if (colon < 0) {
            throw malformed(manifestPath, field);
        }
        int arrayStart = nextNonWhitespace(json, colon + 1);
        if (arrayStart < 0 || json.charAt(arrayStart) != '[') {
            throw malformed(manifestPath, field);
        }
        int arrayEnd = matching(json, arrayStart, '[', ']', manifestPath);
        List<String> values = new ArrayList<>();
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
            if (character != '"') {
                throw malformed(manifestPath, field);
            }
            StringValue value = stringValue(json, index, manifestPath);
            values.add(value.value());
            index = value.endIndex() + 1;
        }
        return List.copyOf(values);
    }

    private static StringValue stringValue(String json, int quoteStart, Path manifestPath) {
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
                return new StringValue(value.toString(), index);
            }
            value.append(character);
        }
        throw malformed(manifestPath, "string");
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

    private record StringValue(String value, int endIndex) {}
}
