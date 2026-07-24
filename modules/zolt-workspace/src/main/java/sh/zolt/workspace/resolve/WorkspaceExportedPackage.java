package sh.zolt.workspace.resolve;

import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockArtifactVariant;

/** One external API artifact re-exported by a workspace member. */
record WorkspaceExportedPackage(PackageId packageId, LockArtifactVariant variant) {
}
