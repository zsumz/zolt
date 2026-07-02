package sh.zolt.explain.emit;

import sh.zolt.project.ProjectConfig;

/**
 * Renders a {@link ProjectConfig} into zolt.toml text.
 *
 * <p>zolt-explain does not depend on zolt-toml. The CLI injects the real
 * {@code ZoltTomlWriter.write(ProjectConfig)} serializer through this indirection so the
 * dependency direction stays explicit, mirroring the {@code ProjectConfigWriter} pattern used by
 * {@code ProjectInitializer}.
 */
@FunctionalInterface
public interface ProjectConfigRenderer {
    String render(ProjectConfig config);
}
