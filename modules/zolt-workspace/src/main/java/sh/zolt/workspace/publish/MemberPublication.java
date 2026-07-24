package sh.zolt.workspace.publish;

import sh.zolt.project.RepositoryCredentialSettings;
import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.publish.PublishSettings;
import java.nio.file.Path;
import java.util.Map;

/**
 * One publishable member's fully-resolved publication: the reused single-project dry-run plan (with
 * its supplementals, SBOM, checksum and signature plans) plus the publish settings and repository
 * credentials the Phase-2 uploaders need to resolve authentication and signing. Kept internal to the
 * workspace publish package; {@link #toReportMember()} projects it to the public display record.
 */
record MemberPublication(
        Path memberRoot,
        String memberPath,
        String coordinate,
        boolean bom,
        PublishDryRunPlan plan,
        PublishSettings publish,
        Map<String, RepositoryCredentialSettings> repositoryCredentials) {

    WorkspacePublishReport.Member toReportMember() {
        return new WorkspacePublishReport.Member(memberPath, coordinate, bom, plan);
    }
}
