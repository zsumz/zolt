package sh.zolt.quarkus.testsupport;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class QuarkusProvidedRuntime {
    private static final List<String> PROVIDED_JARS = List.of(
            "io/quarkus/quarkus-builder/3.33.2/quarkus-builder-3.33.2.jar",
            "io/quarkus/quarkus-junit/3.33.2/quarkus-junit-3.33.2.jar",
            "io/quarkus/quarkus-core-deployment/3.33.2/quarkus-core-deployment-3.33.2.jar",
            "io/quarkus/quarkus-arc-deployment/3.33.2/quarkus-arc-deployment-3.33.2.jar",
            "io/quarkus/quarkus-bootstrap-app-model/3.33.2/quarkus-bootstrap-app-model-3.33.2.jar",
            "io/quarkus/quarkus-fs-util/1.3.0/quarkus-fs-util-1.3.0.jar",
            "io/smallrye/jandex/3.5.3/jandex-3.5.3.jar",
            "org/wildfly/common/wildfly-common/2.0.1/wildfly-common-2.0.1.jar");

    private QuarkusProvidedRuntime() {
    }

    public static URLClassLoader open() throws IOException {
        List<URL> urls = new ArrayList<>();
        Path repoRoot = repoRoot();
        try (Stream<Path> paths = Files.walk(repoRoot.resolve("modules"), 3)) {
            for (Path classesDirectory : paths
                    .filter(Files::isDirectory)
                    .filter(path -> path.endsWith("target/classes") || path.endsWith("target/test-classes"))
                    .sorted()
                    .toList()) {
                urls.add(classesDirectory.toUri().toURL());
            }
        }
        Path cacheRoot = repoRoot.resolve(".zolt/cache");
        for (String jar : PROVIDED_JARS) {
            urls.add(cacheRoot.resolve(jar).toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    public static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("zolt.lock"))
                    && Files.exists(current.resolve("modules/zolt-quarkus"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Could not locate Zolt repository root.");
    }
}
