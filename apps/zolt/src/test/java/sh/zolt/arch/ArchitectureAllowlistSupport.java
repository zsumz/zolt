package sh.zolt.arch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

final class ArchitectureAllowlistSupport {
    private ArchitectureAllowlistSupport() {
    }

    static <T> Map<String, T> readAllowlist(
            Path path,
            Function<String, Optional<T>> parser,
            Function<T, String> pathExtractor,
            String duplicateMessagePrefix) throws IOException {
        Map<String, T> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            Optional<T> entry = parser.apply(line);
            if (entry.isEmpty()) {
                continue;
            }
            T allowlistEntry = entry.orElseThrow();
            String allowlistPath = pathExtractor.apply(allowlistEntry);
            T previous = entries.put(allowlistPath, allowlistEntry);
            if (previous != null) {
                throw new IllegalArgumentException(duplicateMessagePrefix + allowlistPath);
            }
        }
        return entries;
    }
}
