package com.zolt.explain.emit;

import com.zolt.workspace.WorkspaceConfig;

/**
 * Renders a {@link WorkspaceConfig} into the root {@code [workspace]} zolt.toml text.
 *
 * <p>zolt-explain does not depend on zolt-toml. The CLI injects the real
 * {@code WorkspaceTomlWriter.write(WorkspaceConfig)} serializer through this indirection so the
 * dependency direction stays explicit, mirroring {@link ProjectConfigRenderer}.
 */
@FunctionalInterface
public interface WorkspaceConfigRenderer {
    String render(WorkspaceConfig config);
}
