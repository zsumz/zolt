package sh.zolt.arch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class ArchitectureSourceFiles {
    private ArchitectureSourceFiles() {
    }

    static List<Path> javaFiles(List<Path> sourceRoots) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        for (Path sourceRoot : sourceRoots) {
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .sorted()
                        .forEach(javaFiles::add);
            }
        }
        javaFiles.sort(Comparator.naturalOrder());
        return List.copyOf(javaFiles);
    }
}
