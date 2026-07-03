package sh.zolt.explain.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

/** Detects a JPMS {@code module-info.java} that the audited Java version is too old to compile. */
final class MavenModuleInfoDetection {
    private MavenModuleInfoDetection() {
    }

    /**
     * The first primary source root that holds a {@code module-info.java} while the audited Java
     * feature version is under 9 (JPMS needs Java 9+). Empty when the Java version is unknown or 9+,
     * or when no source root contains a {@code module-info.java}.
     */
    static Optional<String> moduleInfoUnderJavaBelow9(
            MavenProjectInspection inspection, Path projectDirectory) {
        OptionalInt feature = javaFeatureVersion(inspection.javaVersion());
        if (feature.isEmpty() || feature.getAsInt() >= 9) {
            return Optional.empty();
        }
        for (String root : inspection.sourceRoots()) {
            Path moduleInfo = projectDirectory.resolve(root).resolve("module-info.java");
            if (Files.isRegularFile(moduleInfo)) {
                return Optional.of(root);
            }
        }
        return Optional.empty();
    }

    private static OptionalInt javaFeatureVersion(String javaVersion) {
        if (javaVersion == null) {
            return OptionalInt.empty();
        }
        String value = javaVersion.strip();
        if (value.startsWith("1.")) {
            value = value.substring(2);
        }
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(value.substring(0, end)));
    }
}
