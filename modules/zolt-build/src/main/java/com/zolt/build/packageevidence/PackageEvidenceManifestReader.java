package com.zolt.build.packageevidence;

import com.zolt.build.PackageException;
import com.zolt.build.PackageMergeDecision;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PackageEvidenceManifestReader {
    public PackageEvidenceManifest read(Path manifestPath) {
        try {
            String json = Files.readString(manifestPath, StandardCharsets.UTF_8);
            PackageEvidenceJsonReader reader = new PackageEvidenceJsonReader(json, manifestPath);
            return new PackageEvidenceManifest(
                    reader.requiredString("schema"),
                    reader.requiredString("archive"),
                    reader.requiredString("archiveSha256"),
                    artifacts(reader),
                    uberMergeDecisions(reader));
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not read package evidence manifest at "
                            + manifestPath
                            + ". Check that the file is readable and retry.",
                    exception);
        }
    }

    private static List<PackageMergeDecision> uberMergeDecisions(PackageEvidenceJsonReader reader) {
        List<PackageEvidenceJsonReader> objects = reader.objectArray("uberMergeDecisions");
        if (objects.isEmpty()) {
            return List.of();
        }
        List<PackageMergeDecision> decisions = new ArrayList<>();
        for (PackageEvidenceJsonReader object : objects) {
            decisions.add(new PackageMergeDecision(
                    object.requiredString("kind"),
                    object.requiredString("path"),
                    object.nullableString("target"),
                    object.stringArray("sources")));
        }
        return List.copyOf(decisions);
    }

    private static List<PackageEvidenceArtifact> artifacts(PackageEvidenceJsonReader reader) {
        List<PackageEvidenceJsonReader> objects = reader.objectArray("artifacts");
        if (objects.isEmpty()) {
            return List.of();
        }
        List<PackageEvidenceArtifact> artifacts = new ArrayList<>();
        for (PackageEvidenceJsonReader object : objects) {
            artifacts.add(new PackageEvidenceArtifact(
                    object.requiredString("classifier"),
                    object.requiredString("type"),
                    object.requiredString("path"),
                    object.requiredInt("entries"),
                    object.requiredString("sha256")));
        }
        return List.copyOf(artifacts);
    }
}
