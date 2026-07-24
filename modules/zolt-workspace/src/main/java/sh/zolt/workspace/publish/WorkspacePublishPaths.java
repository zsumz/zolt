package sh.zolt.workspace.publish;

import sh.zolt.workspace.service.Workspace;
import java.nio.file.Path;

/** Well-known locations for workspace-publish staging and resume state, plus root-relative display. */
final class WorkspacePublishPaths {
    private WorkspacePublishPaths() {
    }

    static Path stagingRoot(Workspace workspace) {
        return workspace.root().resolve("target").resolve("zolt-publish").resolve("staging");
    }

    static Path resumeStatePath(Workspace workspace) {
        return workspace.root().resolve("target").resolve("zolt-publish").resolve("resume-state");
    }

    static String displayPath(Workspace workspace, Path path) {
        Path root = workspace.root().toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(root) ? root.relativize(normalized).toString() : normalized.toString();
    }
}
