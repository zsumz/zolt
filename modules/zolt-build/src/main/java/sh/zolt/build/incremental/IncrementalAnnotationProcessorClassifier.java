package sh.zolt.build.incremental;

import sh.zolt.classpath.Classpath;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Classifies an annotation-processor classpath from the ecosystem-standard
 * {@code META-INF/gradle/incremental.annotation.processors} metadata so the planner can
 * record WHY a processor build stays on the full-recompile path. Reads are best-effort:
 * any unreadable metadata degrades to the most conservative (full-recompile) reason.
 */
final class IncrementalAnnotationProcessorClassifier {
    private static final String SERVICE_RESOURCE =
            "META-INF/services/javax.annotation.processing.Processor";
    private static final String INCREMENTAL_RESOURCE =
            "META-INF/gradle/incremental.annotation.processors";

    String fallbackReason(Classpath processorClasspath) {
        List<Path> entries = processorClasspath.entries();
        if (entries.isEmpty()) {
            return "";
        }
        Set<String> activeProcessors = new LinkedHashSet<>();
        Map<String, String> categories = new LinkedHashMap<>();
        for (Path entry : entries) {
            activeProcessors.addAll(readServiceProcessors(entry));
            categories.putAll(readIncrementalCategories(entry));
        }
        if (activeProcessors.isEmpty()) {
            return "processor-metadata-missing";
        }
        for (String processor : activeProcessors) {
            String category = categories.get(processor);
            if (category == null) {
                return "processor-metadata-missing";
            }
            switch (category) {
                case "isolating" -> {
                }
                case "aggregating" -> {
                    return "processor-aggregating";
                }
                case "dynamic" -> {
                    return "processor-dynamic";
                }
                default -> {
                    return "processor-metadata-missing";
                }
            }
        }
        return "processor-generated-outputs-untracked";
    }

    private static List<String> readServiceProcessors(Path entry) {
        return readResource(entry, SERVICE_RESOURCE)
                .map(IncrementalAnnotationProcessorClassifier::parseServiceLines)
                .orElse(List.of());
    }

    private static Map<String, String> readIncrementalCategories(Path entry) {
        return readResource(entry, INCREMENTAL_RESOURCE)
                .map(IncrementalAnnotationProcessorClassifier::parseIncrementalLines)
                .orElse(Map.of());
    }

    private static Optional<String> readResource(Path entry, String resource) {
        try {
            if (Files.isDirectory(entry)) {
                Path file = entry.resolve(resource);
                return Files.isRegularFile(file)
                        ? Optional.of(Files.readString(file, StandardCharsets.UTF_8))
                        : Optional.empty();
            }
            if (Files.isRegularFile(entry)) {
                try (ZipFile zip = new ZipFile(entry.toFile())) {
                    ZipEntry zipEntry = zip.getEntry(resource);
                    if (zipEntry == null) {
                        return Optional.empty();
                    }
                    try (InputStream input = zip.getInputStream(zipEntry)) {
                        return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static List<String> parseServiceLines(String content) {
        List<String> names = new ArrayList<>();
        for (String raw : content.split("\n")) {
            String line = stripComment(raw);
            if (!line.isBlank()) {
                names.add(line);
            }
        }
        return names;
    }

    private static Map<String, String> parseIncrementalLines(String content) {
        Map<String, String> categories = new LinkedHashMap<>();
        for (String raw : content.split("\n")) {
            String line = stripComment(raw);
            int comma = line.indexOf(',');
            if (comma < 0) {
                continue;
            }
            String name = line.substring(0, comma).trim();
            String category = line.substring(comma + 1).trim().toLowerCase(Locale.ROOT);
            int nextComma = category.indexOf(',');
            if (nextComma >= 0) {
                category = category.substring(0, nextComma).trim();
            }
            if (!name.isBlank()) {
                categories.put(name, category);
            }
        }
        return categories;
    }

    private static String stripComment(String raw) {
        int hash = raw.indexOf('#');
        return (hash >= 0 ? raw.substring(0, hash) : raw).trim();
    }
}
