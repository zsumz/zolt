package com.zolt.build.packageevidence;

import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.indent;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.nullableStringField;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.stringArrayField;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.stringField;

import com.zolt.build.packaging.PackageMergeDecision;
import java.util.Comparator;
import java.util.List;

final class PackageMergeDecisionEvidenceWriter {
    private PackageMergeDecisionEvidenceWriter() {
    }

    static void write(StringBuilder json, List<PackageMergeDecision> decisions) {
        List<PackageMergeDecision> sorted = decisions.stream()
                .sorted(Comparator
                        .comparing(PackageMergeDecision::kind)
                        .thenComparing(PackageMergeDecision::path)
                        .thenComparing(decision -> decision.target().orElse(""))
                        .thenComparing(decision -> String.join("\n", decision.sources())))
                .toList();
        indent(json, 1).append("\"uberMergeDecisions\": [");
        if (!sorted.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < sorted.size(); index++) {
                PackageMergeDecision decision = sorted.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "kind", decision.kind(), true);
                stringField(json, 3, "path", decision.path(), true);
                nullableStringField(json, 3, "target", decision.target(), true);
                stringArrayField(json, 3, "sources", decision.sources(), false);
                indent(json, 2).append("}");
                if (index + 1 < sorted.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }
}
