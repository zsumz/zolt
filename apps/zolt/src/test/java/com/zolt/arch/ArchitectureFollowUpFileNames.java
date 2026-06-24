package com.zolt.arch;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ArchitectureFollowUpFileNames {
    private static final Pattern FOLLOW_UP_FILENAME = Pattern.compile("follow-up-(\\d{3,})-.+\\.md");

    private ArchitectureFollowUpFileNames() {
    }

    static Optional<String> numericId(Path path) {
        Matcher matcher = FOLLOW_UP_FILENAME.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    static Optional<String> followUpId(Path path) {
        return numericId(path).map(id -> "follow-up-" + id);
    }

    static int number(String followUpId) {
        if (followUpId.startsWith("follow-up-")) {
            return Integer.parseInt(followUpId.substring("follow-up-".length()));
        }
        return Integer.parseInt(followUpId);
    }
}
