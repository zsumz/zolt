package com.zolt.build;

import static com.zolt.build.PackageEvidenceJsonFields.booleanField;
import static com.zolt.build.PackageEvidenceJsonFields.displayPath;
import static com.zolt.build.PackageEvidenceJsonFields.indent;
import static com.zolt.build.PackageEvidenceJsonFields.nullableStringField;
import static com.zolt.build.PackageEvidenceJsonFields.stringField;

import com.zolt.generated.GeneratedSourceEvidence;
import com.zolt.generated.GeneratedSourceEvidenceService;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.List;

final class PackageEvidenceGeneratedSourceWriter {
    private final GeneratedSourceEvidenceService generatedSourceEvidenceService;

    PackageEvidenceGeneratedSourceWriter() {
        this(new GeneratedSourceEvidenceService());
    }

    PackageEvidenceGeneratedSourceWriter(GeneratedSourceEvidenceService generatedSourceEvidenceService) {
        this.generatedSourceEvidenceService = generatedSourceEvidenceService;
    }

    void write(StringBuilder json, Path projectRoot, ProjectConfig config) {
        write(json, projectRoot, generatedSourceEvidenceService.evidence(projectRoot, config.build()));
    }

    static void write(
            StringBuilder json,
            Path projectRoot,
            List<GeneratedSourceEvidence> generatedSources) {
        indent(json, 1).append("\"generatedSources\": [");
        if (!generatedSources.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < generatedSources.size(); index++) {
                GeneratedSourceEvidence evidence = generatedSources.get(index);
                GeneratedSourceStep step = evidence.step();
                indent(json, 2).append("{\n");
                stringField(json, 3, "id", evidence.id(), true);
                stringField(json, 3, "sourceRootId", evidence.sourceRootId(), true);
                stringField(json, 3, "scope", evidence.scope(), true);
                stringField(json, 3, "kind", step.kind().configValue(), true);
                stringField(json, 3, "language", step.language(), true);
                stringField(json, 3, "output", displayPath(projectRoot, evidence.output()), true);
                booleanField(json, 3, "required", step.required(), true);
                booleanField(json, 3, "clean", step.clean(), true);
                stringField(json, 3, "ownership", evidence.ownership(), true);
                stringField(json, 3, "compileLane", evidence.compileLane(), true);
                stringField(json, 3, "freshness", evidence.freshness(), true);
                stringField(json, 3, "toolArtifact", evidence.toolArtifact(), true);
                nullableStringField(json, 3, "toolVersionRef", step.openApi().toolVersionRef(), true);
                stringField(json, 3, "toolFingerprint", evidence.toolFingerprint(), true);
                stringField(json, 3, "optionsFingerprint", evidence.optionsFingerprint(), true);
                PackageEvidencePathWriter.writeFingerprintedPaths(json, 3, "inputs", projectRoot, evidence.inputs(), false);
                indent(json, 2).append("}");
                if (index + 1 < generatedSources.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }
}
