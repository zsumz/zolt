package sh.zolt.sbom;

import java.util.List;

/** The grouped license view behind {@code zolt licenses}. Groups are sorted by label. */
public record LicenseReport(List<LicenseGroup> groups) {
    public LicenseReport {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    public boolean hasUnknown() {
        return groups.stream().anyMatch(group -> group.status() == SbomLicenseStatus.UNKNOWN);
    }

    public boolean hasUnmapped() {
        return groups.stream().anyMatch(group -> group.status() == SbomLicenseStatus.UNMAPPED);
    }
}
