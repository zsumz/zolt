package com.zolt.quality;

import com.zolt.project.ProjectConfig;
import com.zolt.workspace.Workspace;
import com.zolt.workspace.WorkspaceMember;
import com.zolt.workspace.WorkspaceSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class QualityExecutionContextRunner {
    private final ExecutionContextQualityCheck executionContextQualityCheck;
    private final CredentialQualityCheck credentialQualityCheck;
    private final ExecutionEvidenceQualityCheck executionEvidenceQualityCheck;
    private final PublishDryRunQualityCheck publishDryRunQualityCheck;

    QualityExecutionContextRunner(
            ExecutionContextQualityCheck executionContextQualityCheck,
            CredentialQualityCheck credentialQualityCheck,
            ExecutionEvidenceQualityCheck executionEvidenceQualityCheck,
            PublishDryRunQualityCheck publishDryRunQualityCheck) {
        this.executionContextQualityCheck = executionContextQualityCheck;
        this.credentialQualityCheck = credentialQualityCheck;
        this.executionEvidenceQualityCheck = executionEvidenceQualityCheck;
        this.publishDryRunQualityCheck = publishDryRunQualityCheck;
    }

    List<QualityCheckResult> checkProject(QualityCheckRequest request, ProjectConfig config) {
        List<QualityCheckResult> results = new ArrayList<>();
        results.addAll(executionContextQualityCheck.check(
                Optional.empty(),
                request.projectRoot(),
                request.context()));
        results.addAll(credentialQualityCheck.checkRepositoryCredentials(
                Optional.empty(),
                config,
                request.context()));
        results.addAll(credentialQualityCheck.checkPublishCredentials(
                Optional.empty(),
                request.projectRoot(),
                config,
                request.context()));
        results.addAll(credentialQualityCheck.checkResourceTokens(
                Optional.empty(),
                config,
                request.context()));
        results.addAll(executionEvidenceQualityCheck.checkTestReports(
                Optional.empty(),
                request.projectRoot(),
                request.reportsDir(),
                request.reportsDir(),
                java.nio.file.Path.of(config.build().outputRoot()),
                request.context()));
        results.addAll(executionEvidenceQualityCheck.checkCoverageReports(
                Optional.empty(),
                request.projectRoot(),
                request.coverageDir(),
                request.coverageDir(),
                java.nio.file.Path.of(config.build().outputRoot()),
                request.context()));
        results.addAll(publishDryRunQualityCheck.check(
                Optional.empty(),
                request.projectRoot(),
                request.context(),
                request.requirePublishDryRun()));
        return List.copyOf(results);
    }

    List<QualityCheckResult> checkWorkspace(
            QualityCheckRequest request,
            Workspace workspace,
            WorkspaceSelection selection,
            Map<String, WorkspaceMember> members) {
        List<QualityCheckResult> results = new ArrayList<>();
        results.addAll(executionContextQualityCheck.check(
                Optional.empty(),
                workspace.root(),
                request.context()));
        if (request.context() != QualityCheckContext.CI) {
            return List.copyOf(results);
        }
        for (String memberPath : selection.includedMembers()) {
            WorkspaceMember member = members.get(memberPath);
            Optional<String> memberName = Optional.of(member.path());
            results.addAll(credentialQualityCheck.checkRepositoryCredentials(
                    memberName,
                    member.config(),
                    request.context()));
            results.addAll(credentialQualityCheck.checkPublishCredentials(
                    memberName,
                    member.directory(),
                    member.config(),
                    request.context()));
            results.addAll(credentialQualityCheck.checkResourceTokens(
                    memberName,
                    member.config(),
                    request.context()));
            results.addAll(publishDryRunQualityCheck.check(
                    memberName,
                    member.directory(),
                    request.context(),
                    request.requirePublishDryRun()));
        }
        for (String memberPath : selection.selectedMembers()) {
            WorkspaceMember member = members.get(memberPath);
            Optional<String> memberName = Optional.of(member.path());
            results.addAll(executionEvidenceQualityCheck.checkTestReports(
                    memberName,
                    member.directory(),
                    request.reportsDir() == null ? null : request.reportsDir().resolve(member.path()),
                    request.reportsDir(),
                    java.nio.file.Path.of(member.config().build().outputRoot()),
                    request.context()));
            results.addAll(executionEvidenceQualityCheck.checkCoverageReports(
                    memberName,
                    member.directory(),
                    request.coverageDir(),
                    request.coverageDir(),
                    java.nio.file.Path.of(member.config().build().outputRoot()),
                    request.context()));
        }
        return List.copyOf(results);
    }
}
