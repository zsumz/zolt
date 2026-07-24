package sh.zolt.workspace.publish;

import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Produces the per-member CycloneDX SBOM attached by {@code zolt publish --workspace --sbom}. The
 * workspace service invokes it once per non-BOM member with that member's directory, policy-merged
 * config, and projected publish lock, and attaches the returned file as a supplemental artifact.
 *
 * <p>The implementation lives in the CLI layer (apps/zolt), which already depends on {@code zolt-sbom}
 * and generates the file exactly as single-project {@code --sbom} does; wiring it as an injected
 * function keeps {@code zolt-workspace} free of a {@code zolt-sbom} dependency. A BOM member has no
 * resolved graph and is never passed here (it receives no SBOM by design).
 */
@FunctionalInterface
public interface WorkspaceMemberSbomGenerator {

    /** Generates and writes the member's SBOM, returning its path, or empty when no SBOM applies. */
    Optional<Path> generate(Path memberDirectory, ProjectConfig memberConfig, ZoltLockfile memberLock);

    /** A generator that attaches nothing — used when {@code --sbom} is not requested. */
    static WorkspaceMemberSbomGenerator disabled() {
        return (memberDirectory, memberConfig, memberLock) -> Optional.empty();
    }
}
