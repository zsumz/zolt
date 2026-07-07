package sh.zolt.quarkus.testsupport;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class QuarkusTestRuntimeClasspath {
    private QuarkusTestRuntimeClasspath() {
    }

    static List<URL> currentJvmUrls() {
        List<URL> urls = new ArrayList<>();
        String classpath = System.getProperty("java.class.path", "");
        if (classpath.isBlank()) {
            return urls;
        }
        for (String entry : classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                urls.add(url(Path.of(entry)));
            }
        }
        return urls;
    }

    static List<Path> existingRepoCacheJars(Path repoRoot, List<String> relativeJars) {
        Path cacheRoot = repoRoot.resolve(".zolt/cache");
        List<Path> jars = new ArrayList<>();
        for (String relativeJar : relativeJars) {
            Path jar = cacheRoot.resolve(relativeJar);
            if (Files.isRegularFile(jar)) {
                jars.add(jar);
            }
        }
        return jars;
    }

    static URL url(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException exception) {
            throw new AssertionError("Could not convert classpath entry to URL: " + path, exception);
        }
    }
}
