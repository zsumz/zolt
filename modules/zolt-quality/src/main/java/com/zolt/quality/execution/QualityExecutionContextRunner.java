package com.zolt.quality.execution;

import com.zolt.lockfile.toml.ZoltLockfileReader;
import com.zolt.project.ProjectConfig;
import com.zolt.publish.PublishDryRunService;
import com.zolt.publish.PublishSettingsReader;
import com.zolt.quality.QualityCheckContext;
import com.zolt.quality.QualityCheckRequest;
import com.zolt.quality.QualityCheckResult;
import com.zolt.workspace.service.Workspace;
import com.zolt.workspace.service.WorkspaceMember;
import com.zolt.workspace.service.WorkspaceSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class QualityExecutionContextRunner {
    private final ExecutionContextQualityCheck executionContextQualityCheck;
    private final CredentialQualityCheck credentialQualityCheck;
    private final ExecutionEvidenceQualityCheck executionEvidenceQualityCheck;
    private final PublishDryRunQualityCheck publishDryRunQualityCheck;

    private QualityExecutionContextRunner(
            ExecutionContextQualityCheck executionContextQualityCheck,
            CredentialQualityCheck credentialQualityCheck,
            ExecutionEvidenceQualityCheck executionEvidenceQualityCheck,
            PublishDryRunQualityCheck publishDryRunQualityCheck) {
        this.executionContextQualityCheck = executionContextQualityCheck;
        this.credentialQualityCheck = credentialQualityCheck;
        this.executionEvidenceQualityCheck = executionEvidenceQualityCheck;
        this.publishDryRunQualityCheck = publishDryRunQualityCheck;
    }

    public static QualityExecutionContextRunner create(
            ZoltLockfileReader lockfileReader,
            PublishSettingsReader publishSettingsReader,
            Function<String, String> environment,
            PublishDryRunService publishDryRunService) {
        return new QualityExecutionContextRunner(
                new ExecutionContextQualityCheck(lockfileReader),
                new CredentialQualityCheck(publishSettingsReader, environment),
                new ExecutionEvidenceQualityCheck(),
                new PublishDryRunQualityCheck(publishDryRunService));
    }

    public List<QualityCheckResult> checkProject(QualityCheckRequest request, ProjectConfig config) {
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

    public List<QualityCheckResult> checkWorkspace(
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
