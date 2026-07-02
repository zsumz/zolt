package sh.zolt.test;

import java.nio.file.Path;
import java.util.List;

public record TestInventoryEntry(
        String className,
        Path outputRoot,
        Path classFile,
        List<String> matchedClassNamePatterns,
        String engineId,
        List<String> tags) {
    public TestInventoryEntry {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("Test inventory entry requires a class name.");
        }
        if (outputRoot == null) {
            throw new IllegalArgumentException("Test inventory entry requires an output root.");
        }
        if (classFile == null) {
            throw new IllegalArgumentException("Test inventory entry requires a class file.");
        }
        outputRoot = outputRoot.toAbsolutePath().normalize();
        classFile = classFile.toAbsolutePath().normalize();
        matchedClassNamePatterns = List.copyOf(matchedClassNamePatterns);
        engineId = engineId == null ? "" : engineId;
        tags = List.copyOf(tags);
    }
}
