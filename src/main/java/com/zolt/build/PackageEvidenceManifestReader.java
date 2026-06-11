package com.zolt.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PackageEvidenceManifestReader {
    public PackageEvidenceManifest read(Path manifestPath) {
        try {
            String json = Files.readString(manifestPath, StandardCharsets.UTF_8);
            return new PackageEvidenceManifest(
                    requiredString(json, "schema", manifestPath),
                    requiredString(json, "archive", manifestPath),
                    requiredString(json, "archiveSha256", manifestPath));
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not read package evidence manifest at "
                            + manifestPath
                            + ". Check that the file is readable and retry.",
                    exception);
        }
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
