package sh.zolt.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class ProjectPaths {
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final Pattern SAFE_FILENAME_COMPONENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]*");

    private ProjectPaths() {
    }

    public static Path root(Path projectDirectory) {
        return projectDirectory.toAbsolutePath().normalize();
    }

    public static Path input(Path projectRoot, String key, String configuredPath) {
        return projectPath(projectRoot, key, configuredPath, true);
    }

    public static Path output(Path projectRoot, String key, String configuredPath) {
        return projectPath(projectRoot, key, configuredPath, false);
    }

    public static Path existingRoot(Path projectRoot, String key, String configuredPath) {
        Path path = input(projectRoot, key, configuredPath);
        if (Files.exists(path)) {
            requireExistingInsideProject(projectRoot, key, configuredPath, path);
        }
        return path;
    }

    public static String filenameComponent(String key, String value) {
        if (value == null || value.isBlank() || !SAFE_FILENAME_COMPONENT.matcher(value).matches()) {
            throw new ProjectPathException(
                    "Invalid "
                            + key
                            + " value `"
                            + value
                            + "` cannot be used in derived file names. Use letters, digits, '.', '_', '-', or '+', and do not use path separators.");
        }
        return value;
    }

    private static void requireExistingAncestorInsideProject(
            Path projectRoot,
            String key,
            String configuredPath,
            Path path) {
        Path ancestor = path.getParent();
        while (ancestor != null && !Files.exists(ancestor)) {
            ancestor = ancestor.getParent();
        }
        if (ancestor != null) {
            requireExistingInsideProject(projectRoot, key, configuredPath, ancestor);
        }
    }

    public static boolean isRegularFileInsideProject(Path projectRoot, String key, Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        requireExistingInsideProject(projectRoot, key, path.toString(), path);
        return true;
    }

    public static void requireExistingInsideProject(
            Path projectRoot,
            String key,
            String configuredPath,
            Path path) {
        Path realProjectRoot = realProjectRoot(projectRoot);
        try {
            Path realPath = path.toRealPath();
            if (!realPath.startsWith(realProjectRoot)) {
                throw new ProjectPathException(
                        "Invalid "
                                + key
                                + " path `"
                                + configuredPath
                                + "` resolved through symlinks to "
                                + realPath
                                + ". Use a project-relative path under "
                                + realProjectRoot
                                + ".");
            }
        } catch (IOException exception) {
            throw new ProjectPathException(
                    "Could not validate "
                            + key
                            + " path `"
                            + configuredPath
                            + "` at "
                            + path
                            + ". Check that the path is readable.",
                    exception);
        }
    }

    private static Path projectPath(
            Path projectRoot,
            String key,
            String configuredPath,
            boolean allowProjectRoot) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw invalid(key, configuredPath, projectRoot, projectRoot);
        }
        if (isWindowsAbsolute(configuredPath) || configuredPath.startsWith("\\\\")) {
            throw invalid(key, configuredPath, projectRoot, Path.of(configuredPath));
        }
        Path configured = Path.of(configuredPath);
        Path path = projectRoot.resolve(configured).normalize();
        if (configured.isAbsolute()
                || !path.startsWith(projectRoot)
                || (!allowProjectRoot && path.equals(projectRoot))) {
            throw invalid(key, configuredPath, projectRoot, path);
        }
        if (Files.exists(path)) {
            requireExistingInsideProject(projectRoot, key, configuredPath, path);
        } else if (!allowProjectRoot) {
            requireExistingAncestorInsideProject(projectRoot, key, configuredPath, path);
        }
        return path;
    }

    private static ProjectPathException invalid(
            String key,
            String configuredPath,
            Path projectRoot,
            Path resolvedPath) {
        return new ProjectPathException(
                "Invalid "
                        + key
                        + " path `"
                        + configuredPath
                        + "` resolved to "
                        + resolvedPath
                        + ". Use a project-relative path under "
                        + projectRoot
                        + ".");
    }

    private static boolean isWindowsAbsolute(String configuredPath) {
        return WINDOWS_ABSOLUTE.matcher(configuredPath).matches();
    }

    private static Path realProjectRoot(Path projectRoot) {
        try {
            return projectRoot.toRealPath();
        } catch (IOException exception) {
            throw new ProjectPathException(
                    "Could not validate project root "
                            + projectRoot
                            + ". Check that the project directory exists and is readable.",
                    exception);
        }
    }
}
