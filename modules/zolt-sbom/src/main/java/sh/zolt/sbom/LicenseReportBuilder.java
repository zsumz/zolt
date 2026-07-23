package sh.zolt.sbom;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Builds the grouped {@link LicenseReport} from assembled components and their resolved licenses.
 * Deterministic: groups sorted by label, dependencies within a group sorted (and deduplicated) by
 * coordinate. A component with no resolved license (or an empty index entry) is reported as UNKNOWN.
 */
public final class LicenseReportBuilder {
    public LicenseReport build(List<SbomComponent> components, LicenseIndex index) {
        TreeMap<String, GroupAccumulator> groups = new TreeMap<>();
        for (SbomComponent component : components) {
            String coordinate = component.group() + ":" + component.name() + ":" + component.version();
            List<SbomLicense> licenses = index.forCoordinate(coordinate);
            if (licenses.isEmpty()) {
                licenses = List.of(SbomLicense.unknown());
            }
            for (SbomLicense license : licenses) {
                GroupAccumulator group = groups.computeIfAbsent(
                        license.label(),
                        label -> new GroupAccumulator(label, license.status(), license.url()));
                group.components.putIfAbsent(coordinate, new LicenseComponentRef(coordinate, component.purl()));
            }
        }

        List<LicenseGroup> result = new ArrayList<>();
        for (GroupAccumulator group : groups.values()) {
            result.add(new LicenseGroup(
                    group.label,
                    group.status,
                    group.url,
                    List.copyOf(group.components.values())));
        }
        return new LicenseReport(result);
    }

    private static final class GroupAccumulator {
        private final String label;
        private final SbomLicenseStatus status;
        private final Optional<String> url;
        private final TreeMap<String, LicenseComponentRef> components = new TreeMap<>();

        private GroupAccumulator(String label, SbomLicenseStatus status, Optional<String> url) {
            this.label = label;
            this.status = status;
            this.url = url;
        }
    }
}
