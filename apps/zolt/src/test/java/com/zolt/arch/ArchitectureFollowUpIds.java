package com.zolt.arch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class ArchitectureFollowUpIds {
    private static final Pattern FOLLOW_UP_FILENAME = Pattern.compile("(follow-up-\\d{3,})-.+\\.md");

    private ArchitectureFollowUpIds() {
    }

    static Set<String> read(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return Set.of();
        }
        try (Stream<Path> paths = Files.list(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(ArchitectureFollowUpIds::followUpId)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparingInt(ArchitectureFollowUpIds::followUpNumber))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private static Optional<String> followUpId(Path path) {
        Matcher matcher = FOLLOW_UP_FILENAME.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    private static int followUpNumber(String followUpId) {
        return Integer.parseInt(followUpId.substring("follow-up-".length()));
    }
}
