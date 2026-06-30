package com.zolt.build.packageevidence;

import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.booleanField;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.displayPath;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.indent;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.nullablePathField;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.nullableStringField;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.stringArrayField;
import static com.zolt.build.packageevidence.PackageEvidenceJsonFields.stringField;

import com.zolt.build.packaging.PackageArtifact;
import com.zolt.build.PackageException;
import com.zolt.build.packageplan.PackagePlan;
import com.zolt.build.packageplan.PackagePlanDependency;
import com.zolt.build.packaging.PackageResult;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ResourceFilteringSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PackageEvidenceManifestWriter {
    private static final String SCHEMA = "zolt.package-evidence.v1";

    private final PackageEvidenceGeneratedSourceWriter generatedSourceWriter;
    private final PackageResourceEvidence packageResourceEvidence;

    public PackageEvidenceManifestWriter() {
        this(new PackageEvidenceGeneratedSourceWriter(), new PackageResourceEvidence());
    }

    PackageEvidenceManifestWriter(
            PackageEvidenceGeneratedSourceWriter generatedSourceWriter,
            PackageResourceEvidence packageResourceEvidence) {
        this.generatedSourceWriter = generatedSourceWriter;
        this.packageResourceEvidence = packageResourceEvidence;
    }

    public Path write(
            Path projectDirectory,
            ProjectConfig config,
            PackagePlan plan,
            PackageResult result,
            List<PackageArtifact> artifacts) {
        Path projectRoot = projectDirectory.toAbsolutePath().normalize();
        Path manifestPath = evidenceManifestPath(result.jarPath());
        try {
            Files.createDirectories(manifestPath.getParent());
            Files.writeString(
                    manifestPath,
                    json(projectRoot, config, plan, result, artifacts),
                    StandardCharsets.UTF_8);
            return manifestPath;
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not write package evidence manifest at "
                            + manifestPath
                            + ". Check that target/ is writable and retry.",
                    exception);
        }
    }

    public static Path evidenceManifestPath(Path artifactPath) {
        return artifactPath.resolveSibling(artifactPath.getFileName() + ".zolt-package.json");
    }

    private String json(
            Path projectRoot,
            ProjectConfig config,
            PackagePlan plan,
            PackageResult result,
            List<PackageArtifact> artifacts) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        stringField(json, 1, "schema", SCHEMA, true);
        project(json, config);
        json.append(",\n");
        packageInfo(json, projectRoot, config, plan, result);
        json.append(",\n");
        PackageEvidenceArtifactWriter.write(json, projectRoot, result, artifacts);
        json.append(",\n");
        dependencies(json, plan.dependencies());
        json.append(",\n");
        PackageMergeDecisionEvidenceWriter.write(json, result.mergeDecisions());
        json.append(",\n");
        generatedSourceWriter.write(json, projectRoot, config);
        json.append(",\n");
        resourceFiltering(json, projectRoot, packageResourceEvidence.collect(projectRoot, config.build()));
        json.append("\n}\n");
        return json.toString();
    }

    private static void project(StringBuilder json, ProjectConfig config) {
        indent(json, 1).append("\"project\": {\n");
        stringField(json, 2, "group", config.project().group(), true);
        stringField(json, 2, "name", config.project().name(), true);
        stringField(json, 2, "version", config.project().version(), true);
        nullableStringField(json, 2, "main", config.project().main(), false);
        indent(json, 1).append("}");
    }

    private static void packageInfo(
            StringBuilder json,
            Path projectRoot,
            ProjectConfig config,
            PackagePlan plan,
            PackageResult result) {
        indent(json, 1).append("\"package\": {\n");
        stringField(json, 2, "mode", result.mode().configValue(), true);
        stringField(json, 2, "archive", displayPath(projectRoot, result.jarPath()), true);
        stringField(json, 2, "applicationOutput", displayPath(projectRoot, plan.applicationOutput()), true);
        stringField(json, 2, "applicationLayout", plan.applicationLayout(), true);
        nullablePathField(json, 2, "runtimeClasspath", projectRoot, result.runtimeClasspathPath(), true);
        nullableStringField(json, 2, "startClass", config.project().main(), true);
        stringField(json, 2, "archiveSha256", PackageEvidenceChecksums.sha256(result.jarPath()), false);
        indent(json, 1).append("}");
    }

    private static void dependencies(StringBuilder json, List<PackagePlanDependency> dependencies) {
        indent(json, 1).append("\"dependencies\": [");
        if (!dependencies.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < dependencies.size(); index++) {
                PackagePlanDependency dependency = dependencies.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "coordinate", dependency.coordinate(), true);
                stringField(json, 3, "version", dependency.version(), true);
                stringField(json, 3, "scope", dependency.scope().lockfileName(), true);
                stringArrayField(json, 3, "lanes", dependency.lanes(), true);
                booleanField(json, 3, "packageDefault", dependency.packageDefault(), true);
                stringField(json, 3, "laneDisposition", dependency.laneDisposition(), true);
                stringField(json, 3, "disposition", dependency.disposition(), true);
                stringField(json, 3, "rule", dependency.ruleName(), true);
                stringField(json, 3, "location", dependency.location(), true);
                stringField(json, 3, "reason", dependency.reason(), true);
                stringArrayField(json, 3, "policies", dependency.policies(), false);
                indent(json, 2).append("}");
                if (index + 1 < dependencies.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void resourceFiltering(
            StringBuilder json,
            Path projectRoot,
            PackageResourceEvidence.ResourceEvidence evidence) {
        ResourceFilteringSettings filtering = evidence.filtering();
        indent(json, 1).append("\"resourceFiltering\": {\n");
        booleanField(json, 2, "enabled", filtering.enabled(), true);
        booleanField(json, 2, "testEnabled", filtering.testEnabled(), true);
        stringField(json, 2, "missing", filtering.missing().configValue(), true);
        stringArrayField(json, 2, "includes", filtering.includes(), true);
        tokenSources(json, evidence.tokenSources());
        json.append(",\n");
        stringField(json, 2, "fingerprint", evidence.fingerprint(), true);
        PackageEvidencePathWriter.writeFingerprintedPaths(json, 2, "inputs", projectRoot, evidence.inputs(), false);
        indent(json, 1).append("}");
    }

    private static void tokenSources(
            StringBuilder json,
            List<PackageResourceEvidence.TokenSource> tokens) {
        indent(json, 2).append("\"tokenSources\": [");
        if (!tokens.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < tokens.size(); index++) {
                PackageResourceEvidence.TokenSource token = tokens.get(index);
                indent(json, 3).append("{\n");
                stringField(json, 4, "name", token.name(), true);
                stringField(json, 4, "source", token.source(), false);
                indent(json, 3).append("}");
                if (index + 1 < tokens.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 2);
        }
        json.append("]");
    }
}
