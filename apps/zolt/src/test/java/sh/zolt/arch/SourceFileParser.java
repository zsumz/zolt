package sh.zolt.arch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SourceFileParser {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+)(?:\\.\\*)?\\s*;");

    private SourceFileParser() {
    }

    static SourceFile parse(Path path) throws IOException {
        String packageName = "";
        Set<String> importedPackages = new TreeSet<>();
        for (String line : Files.readAllLines(path)) {
            Matcher packageMatcher = PACKAGE_PATTERN.matcher(line);
            if (packageMatcher.matches()) {
                packageName = topLevelZoltPackage(packageMatcher.group(1)).orElse("");
                continue;
            }
            Matcher importMatcher = IMPORT_PATTERN.matcher(line);
            if (importMatcher.matches()) {
                topLevelZoltPackage(importMatcher.group(1)).ifPresent(importedPackages::add);
            }
        }
        return new SourceFile(packageName, importedPackages);
    }

    private static Optional<String> topLevelZoltPackage(String name) {
        if (!name.startsWith("sh.zolt.")) {
            return Optional.empty();
        }
        String[] parts = name.split("\\.");
        if (parts.length < 3) {
            return Optional.empty();
        }
        return Optional.of(parts[0] + "." + parts[1] + "." + parts[2]);
    }
}
