package sh.zolt.build;

import static sh.zolt.build.packaging.PackageServiceTestSupport.config;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class BuildRequestTest {
    private final Path projectDirectory = Path.of("project");
    private final ProjectConfig config = config(Optional.empty());
    private final Path cacheRoot = Path.of("cache");

    @Test
    void keepsBuildInputsTogether() {
        BuildRequest request = new BuildRequest(projectDirectory, config, cacheRoot, true);

        assertEquals(projectDirectory, request.projectDirectory());
        assertEquals(config, request.config());
        assertEquals(cacheRoot, request.cacheRoot());
        assertTrue(request.offline());
    }

    @Test
    void requiresProjectDirectory() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new BuildRequest(null, config, cacheRoot, false));

        assertEquals("projectDirectory", exception.getMessage());
    }

    @Test
    void requiresConfig() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new BuildRequest(projectDirectory, null, cacheRoot, false));

        assertEquals("config", exception.getMessage());
    }

    @Test
    void requiresCacheRoot() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new BuildRequest(projectDirectory, config, null, false));

        assertEquals("cacheRoot", exception.getMessage());
    }
}
