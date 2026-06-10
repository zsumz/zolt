package com.zolt.project;

import java.util.List;

public record PublicationMetadata(
        String name,
        String description,
        String url,
        String license,
        List<String> developers,
        String scm,
        String issues) {
    public PublicationMetadata {
        name = normalize(name);
        description = normalize(description);
        url = normalize(url);
        license = normalize(license);
        developers = developers == null ? List.of() : List.copyOf(developers);
        scm = normalize(scm);
        issues = normalize(issues);
    }

    public static PublicationMetadata empty() {
        return new PublicationMetadata("", "", "", "", List.of(), "", "");
    }

    public boolean emptyMetadata() {
        return name.isBlank()
                && description.isBlank()
                && url.isBlank()
                && license.isBlank()
                && developers.isEmpty()
                && scm.isBlank()
                && issues.isBlank();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "" : value;
    }
}
