package sh.zolt.sbom;

import sh.zolt.project.ProjectConfig;

/**
 * A workspace member for SBOM aggregation: its declared path (matching the lockfile {@code members}
 * attribution) and its {@link ProjectConfig} (coordinate + authoritative license from config).
 */
public record SbomWorkspaceMember(String path, ProjectConfig config) {
}
