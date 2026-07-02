package sh.zolt.build.packageevidence;

import static sh.zolt.build.packageevidence.PackageEvidenceJsonFields.displayPath;
import static sh.zolt.build.packageevidence.PackageEvidenceJsonFields.indent;
import static sh.zolt.build.packageevidence.PackageEvidenceJsonFields.stringField;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class PackageEvidencePathWriter {
    private PackageEvidencePathWriter() {
    }

    public static void writeFingerprintedPaths(
            StringBuilder json,
            int level,
            String name,
            Path projectRoot,
            List<Path> paths,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!paths.isEmpty()) {
            json.append('\n');
            List<Path> sorted = paths.stream()
                    .sorted(Comparator.comparing(path -> displayPath(projectRoot, path)))
                    .toList();
            for (int index = 0; index < sorted.size(); index++) {
                Path path = sorted.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "path", displayPath(projectRoot, path), true);
                stringField(json, level + 2, "sha256", PackageEvidenceChecksums.fileSha256(path), false);
                indent(json, level + 1).append("}");
                if (index + 1 < sorted.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, level);
        }
        json.append("]");
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }
}
