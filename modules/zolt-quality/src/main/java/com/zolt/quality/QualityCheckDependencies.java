package com.zolt.quality;

import com.zolt.build.PackageEvidenceManifestReader;
import com.zolt.build.PackagePlanService;
import com.zolt.generated.GeneratedSourceEvidenceService;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.policy.DependencyPolicyReportService;
import com.zolt.publish.PublishDryRunService;
import com.zolt.publish.PublishSettingsReader;
import com.zolt.resolve.ResolveService;
import com.zolt.workspace.WorkspaceResolveService;
import java.util.function.Function;

final class QualityCheckDependencies {
    private final GeneratedSourceQualityCheck generatedSourceQualityCheck;
    private final LockfileQualityCheck lockfileQualityCheck;
    private final QualityExecutionContextRunner executionContextRunner;
    private final PackageQualityCheck packageQualityCheck;
    private final DependencyQualityCheck dependencyQualityCheck;

    private QualityCheckDependencies(
            GeneratedSourceQualityCheck generatedSourceQualityCheck,
            LockfileQualityCheck lockfileQualityCheck,
            QualityExecutionContextRunner executionContextRunner,
            PackageQualityCheck packageQualityCheck,
            DependencyQualityCheck dependencyQualityCheck) {
        this.generatedSourceQualityCheck = generatedSourceQualityCheck;
        this.lockfileQualityCheck = lockfileQualityCheck;
        this.executionContextRunner = executionContextRunner;
        this.packageQualityCheck = packageQualityCheck;
        this.dependencyQualityCheck = dependencyQualityCheck;
    }

    static QualityCheckDependencies create(Function<String, String> environment) {
        ZoltLockfileReader lockfileReader = new ZoltLockfileReader();
        return new QualityCheckDependencies(
                new GeneratedSourceQualityCheck(new GeneratedSourceEvidenceService()),
                new LockfileQualityCheck(new ResolveService(), new WorkspaceResolveService(), lockfileReader),
                new QualityExecutionContextRunner(
                        new ExecutionContextQualityCheck(lockfileReader),
                        new CredentialQualityCheck(new PublishSettingsReader(), environment),
                        new ExecutionEvidenceQualityCheck(),
                        new PublishDryRunQualityCheck(new PublishDryRunService())),
                new PackageQualityCheck(new PackagePlanService(), new PackageEvidenceManifestReader()),
                new DependencyQualityCheck(lockfileReader, new DependencyPolicyReportService()));
    }

    GeneratedSourceQualityCheck generatedSourceQualityCheck() {
        return generatedSourceQualityCheck;
    }

    LockfileQualityCheck lockfileQualityCheck() {
        return lockfileQualityCheck;
    }

    QualityExecutionContextRunner executionContextRunner() {
        return executionContextRunner;
    }

    PackageQualityCheck packageQualityCheck() {
        return packageQualityCheck;
    }

    DependencyQualityCheck dependencyQualityCheck() {
        return dependencyQualityCheck;
    }
}
