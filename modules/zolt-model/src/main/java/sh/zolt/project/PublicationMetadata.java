package sh.zolt.project;

import java.util.List;

public record PublicationMetadata(
        String name,
        String description,
        String url,
        String license,
        String licenseUrl,
        List<String> developers,
        List<DeveloperEntry> developerEntries,
        String scm,
        String scmConnection,
        String scmDeveloperConnection,
        String scmTag,
        String issues) {
    public PublicationMetadata {
        name = normalize(name);
        description = normalize(description);
        url = normalize(url);
        license = normalize(license);
        licenseUrl = normalize(licenseUrl);
        developers = developers == null ? List.of() : List.copyOf(developers);
        developerEntries = developerEntries == null ? List.of() : List.copyOf(developerEntries);
        scm = normalize(scm);
        scmConnection = normalize(scmConnection);
        scmDeveloperConnection = normalize(scmDeveloperConnection);
        scmTag = normalize(scmTag);
        issues = normalize(issues);
    }

    /** Backward-compatible constructor for the original name-only metadata shape. */
    public PublicationMetadata(
            String name,
            String description,
            String url,
            String license,
            List<String> developers,
            String scm,
            String issues) {
        this(name, description, url, license, "", developers, List.of(), scm, "", "", "", issues);
    }

    public static PublicationMetadata empty() {
        return new PublicationMetadata("", "", "", "", "", List.of(), List.of(), "", "", "", "", "");
    }

    public boolean emptyMetadata() {
        return name.isBlank()
                && description.isBlank()
                && url.isBlank()
                && license.isBlank()
                && licenseUrl.isBlank()
                && developers.isEmpty()
                && developerEntries.isEmpty()
                && scm.isBlank()
                && scmConnection.isBlank()
                && scmDeveloperConnection.isBlank()
                && scmTag.isBlank()
                && issues.isBlank();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "" : value;
    }
}
