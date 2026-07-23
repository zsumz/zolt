package sh.zolt.sbom;

import java.util.List;

/**
 * Renders a {@link LicenseReport} as a human-readable, deterministic text report grouped by license,
 * with per-dependency attribution and actionable notes for UNMAPPED and UNKNOWN groups.
 */
public final class LicenseReportTextWriter {
    public String write(LicenseReport report) {
        StringBuilder text = new StringBuilder();
        if (report.groups().isEmpty()) {
            text.append("No dependencies in scope.\n");
            return text.toString();
        }
        List<LicenseGroup> groups = report.groups();
        for (int index = 0; index < groups.size(); index++) {
            group(text, groups.get(index));
            if (index + 1 < groups.size()) {
                text.append('\n');
            }
        }
        return text.toString();
    }

    private void group(StringBuilder text, LicenseGroup group) {
        text.append(heading(group)).append(" (").append(group.components().size()).append(")\n");
        for (LicenseComponentRef component : group.components()) {
            text.append("  ").append(component.coordinate()).append('\n');
        }
        note(group).ifPresent(note -> text.append("  note: ").append(note).append('\n'));
    }

    private static String heading(LicenseGroup group) {
        return group.url()
                .filter(url -> group.status() == SbomLicenseStatus.UNMAPPED)
                .map(url -> group.label() + " (" + url + ")")
                .orElse(group.label());
    }

    private static java.util.Optional<String> note(LicenseGroup group) {
        return switch (group.status()) {
            case UNMAPPED -> java.util.Optional.of(
                    "unrecognized license spelling; kept raw. Verify the license manually.");
            case UNKNOWN -> java.util.Optional.of(
                    "no license found in the cached POM chain; run `zolt resolve` to cache POMs, then re-run.");
            case SPDX -> java.util.Optional.empty();
        };
    }
}
