package com.zolt.build.packageevidence;

import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.displayPath;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.indent;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.intField;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.stringField;

import com.zolt.build.PackageArtifact;
import com.zolt.build.PackageResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PackageEvidenceArtifactWriter {
    private PackageEvidenceArtifactWriter() {
    }

    static void write(
            StringBuilder json,
            Path projectRoot,
            PackageResult result,
            List<PackageArtifact> artifacts) {
        List<ArtifactEvidence> entries = entries(result, artifacts);

        indent(json, 1).append("\"artifacts\": [");
        if (!entries.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < entries.size(); index++) {
                ArtifactEvidence entry = entries.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "classifier", entry.classifier(), true);
                stringField(json, 3, "type", entry.type(), true);
                stringField(json, 3, "path", displayPath(projectRoot, entry.path()), true);
                intField(json, 3, "entries", entry.entries(), true);
                stringField(json, 3, "sha256", PackageEvidenceChecksums.sha256(entry.path()), false);
                indent(json, 2).append("}");
                if (index + 1 < entries.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static List<ArtifactEvidence> entries(PackageResult result, List<PackageArtifact> artifacts) {
        List<ArtifactEvidence> entries = new ArrayList<>();
        entries.add(new ArtifactEvidence(
                "main",
                result.mode().configValue(),
                result.jarPath(),
                result.entryCount()));
        for (PackageArtifact artifact : artifacts.stream()
                .sorted(Comparator.comparing(PackageArtifact::classifier))
                .toList()) {
            entries.add(new ArtifactEvidence(
                    artifact.classifier(),
                    "jar",
                    artifact.path(),
                    artifact.entryCount()));
        }
        return entries;
    }

    private record ArtifactEvidence(String classifier, String type, Path path, int entries) {
    }
}
