package sh.zolt.sbom;

import java.util.List;

/**
 * A CycloneDX {@code dependencies[]} edge: {@code ref} depends on each bom-ref in {@code dependsOn}.
 * {@code dependsOn} is caller-sorted and only references surviving (in-scope) components.
 */
public record SbomDependency(String ref, List<String> dependsOn) {
    public SbomDependency {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
