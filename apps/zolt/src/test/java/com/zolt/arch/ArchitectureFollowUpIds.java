package com.zolt.arch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

final class ArchitectureFollowUpIds {
    private ArchitectureFollowUpIds() {
    }

    static Set<String> read(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return Set.of();
        }
        try (Stream<Path> paths = Files.list(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(ArchitectureFollowUpFileNames::followUpId)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparingInt(ArchitectureFollowUpFileNames::number))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
