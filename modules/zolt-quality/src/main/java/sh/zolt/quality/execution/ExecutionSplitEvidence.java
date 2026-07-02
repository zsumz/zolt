package sh.zolt.quality.execution;

import sh.zolt.project.ProjectPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class ExecutionSplitEvidence {
    List<ShardEvidenceManifest> shardManifests(Path root, Path outputRoot) throws IOException {
        Path testShardsDir = ProjectPaths.output(root, "test shard evidence", outputRoot.toString()).resolve("test-shards");
        if (!Files.isDirectory(testShardsDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(testShardsDir)) {
            List<Path> manifests = paths
                    .filter(path -> ProjectPaths.isRegularFileInsideProject(root, "test shard evidence", path))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .toList();
            List<ShardEvidenceManifest> evidence = new ArrayList<>();
            for (Path manifest : manifests) {
                shardManifest(testShardsDir, manifest).ifPresent(evidence::add);
            }
            return List.copyOf(evidence);
        }
    }

    Optional<ShardEvidenceManifest> shardManifest(Path testShardsDir, Path manifestPath) throws IOException {
        Path relative = testShardsDir.relativize(manifestPath);
        if (relative.getNameCount() != 2) {
            return Optional.empty();
        }
        String suiteSegment = relative.getName(0).toString();
        String fileName = relative.getName(1).toString();
        if (!fileName.startsWith("shard-") || !fileName.endsWith(".json")) {
            return Optional.empty();
        }
        String shardSegment = fileName.substring(0, fileName.length() - ".json".length());
        Optional<ShardNumbers> numbers = shardNumbers(shardSegment);
        if (numbers.isEmpty()) {
            return Optional.empty();
        }
        String json = Files.readString(manifestPath);
        return Optional.of(new ShardEvidenceManifest(
                stringField(json, "suite").orElse(suiteSegment),
                suiteSegment,
                shardSegment,
                numbers.orElseThrow().index(),
                numbers.orElseThrow().total(),
                json.contains("\"empty\": true")));
    }

    List<ShardEvidenceManifest> nonEmpty(List<ShardEvidenceManifest> manifests) {
        return manifests.stream()
                .filter(manifest -> !manifest.empty())
                .toList();
    }

    List<String> workerIds(Path manifest) throws IOException {
        if (!Files.isRegularFile(manifest)) {
            return List.of();
        }
        String json = Files.readString(manifest);
        int workersIndex = json.indexOf("\"workers\"");
        if (workersIndex < 0) {
            return List.of();
        }
        int arrayStart = json.indexOf('[', workersIndex);
        int arrayEnd = json.indexOf(']', arrayStart);
        if (arrayStart < 0 || arrayEnd < 0) {
            return List.of();
        }
        List<String> workerIds = new ArrayList<>();
        for (String rawValue : json.substring(arrayStart + 1, arrayEnd).split(",")) {
            String value = rawValue.trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                workerIds.add(value.substring(1, value.length() - 1));
            }
        }
        return List.copyOf(workerIds);
    }

    String shellArgument(String value) {
        if (value != null && value.matches("[A-Za-z0-9._-]+")) {
            return value;
        }
        String text = value == null ? "" : value;
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private Optional<ShardNumbers> shardNumbers(String shardSegment) {
        if (!shardSegment.startsWith("shard-")) {
            return Optional.empty();
        }
        int separator = shardSegment.indexOf("-of-", "shard-".length());
        if (separator < 0) {
            return Optional.empty();
        }
        try {
            int index = Integer.parseInt(shardSegment.substring("shard-".length(), separator));
            int total = Integer.parseInt(shardSegment.substring(separator + "-of-".length()));
            return Optional.of(new ShardNumbers(index, total));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> stringField(String json, String name) {
        int fieldIndex = json.indexOf("\"" + name + "\"");
        if (fieldIndex < 0) {
            return Optional.empty();
        }
        int colon = json.indexOf(':', fieldIndex);
        int valueStart = json.indexOf('"', colon + 1);
        if (colon < 0 || valueStart < 0) {
            return Optional.empty();
        }
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int index = valueStart + 1; index < json.length(); index++) {
            char character = json.charAt(index);
            if (escaped) {
                switch (character) {
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    default -> value.append(character);
                }
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                return Optional.of(value.toString());
            } else {
                value.append(character);
            }
        }
        return Optional.empty();
    }

    record ShardEvidenceManifest(
            String suiteName,
            String suiteSegment,
            String shardSegment,
            int index,
            int total,
            boolean empty) {
        String displayName() {
            return suiteName + "/" + shardSegment;
        }
    }

    private record ShardNumbers(int index, int total) {
    }
}
