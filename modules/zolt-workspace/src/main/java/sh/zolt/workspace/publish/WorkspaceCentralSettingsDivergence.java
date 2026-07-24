package sh.zolt.workspace.publish;

import sh.zolt.publish.PublishCentralSettings;
import sh.zolt.publish.PublishSettings;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@code --central} family publishes as ONE deployment, so every member must share the same Central
 * and signing configuration. This pure helper reports a Phase-1 blocker when members' effective
 * {@code [publish.central]}/{@code [publish.signing]} settings diverge from the anchor (first) member,
 * listing the divergent members and the specific fields (signing key, portal URL, token env,
 * publishing type, deployment name) so an operator can align them. Workspace-root publish config does
 * not inherit to members (only {@code [repositories]}/{@code [platforms]} merge), so divergence means
 * members carry genuinely different publish settings that must be aligned.
 */
final class WorkspaceCentralSettingsDivergence {
    private WorkspaceCentralSettingsDivergence() {
    }

    static List<String> blockers(List<MemberPublication> publications) {
        if (publications.size() <= 1) {
            return List.of();
        }
        MemberPublication anchor = publications.get(0);
        List<String> divergent = new ArrayList<>();
        for (MemberPublication member : publications.subList(1, publications.size())) {
            List<String> fields = divergentFields(anchor.publish(), member.publish());
            if (!fields.isEmpty()) {
                divergent.add("`" + member.coordinate() + "` differs in " + String.join(", ", fields));
            }
        }
        if (divergent.isEmpty()) {
            return List.of();
        }
        return List.of("family Central/signing settings diverge from `" + anchor.coordinate() + "`: "
                + String.join("; ", divergent)
                + ". A --central family publishes as ONE deployment with one signing key, portal URL, token env,"
                + " publishing type, and deployment name. Next: align these across every member's"
                + " [publish.central]/[publish.signing].");
    }

    private static List<String> divergentFields(PublishSettings anchor, PublishSettings member) {
        PublishCentralSettings anchorCentral = anchor.central();
        PublishCentralSettings memberCentral = member.central();
        List<String> fields = new ArrayList<>();
        if (!anchor.signing().keyId().equals(member.signing().keyId())) {
            fields.add("signing key");
        }
        if (!anchorCentral.baseUrl().equals(memberCentral.baseUrl())) {
            fields.add("portal URL");
        }
        if (!anchorCentral.tokenEnv().equals(memberCentral.tokenEnv())) {
            fields.add("token env");
        }
        if (anchorCentral.publishingType() != memberCentral.publishingType()) {
            fields.add("publishing type");
        }
        if (!anchorCentral.deploymentName().equals(memberCentral.deploymentName())) {
            fields.add("deployment name");
        }
        return fields;
    }
}
