package com.zolt.quality;

import com.zolt.build.packageevidence.PackageEvidenceManifestReader;
import com.zolt.build.packageplan.PackagePlanService;
import com.zolt.generated.GeneratedSourceEvidenceService;
import com.zolt.lockfile.toml.ZoltLockfileReader;
import com.zolt.policy.DependencyPolicyReportService;
import com.zolt.publish.PublishDryRunService;
import com.zolt.publish.PublishSettingsReader;
import com.zolt.quality.execution.QualityExecutionContextRunner;
import com.zolt.quality.packaging.PackageQualityCheck;
import com.zolt.resolve.ResolveService;
import com.zolt.workspace.resolve.WorkspaceResolveService;
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
                QualityExecutionContextRunner.create(
                        lockfileReader,
                        new PublishSettingsReader(),
                        environment,
                        new PublishDryRunService()),
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
