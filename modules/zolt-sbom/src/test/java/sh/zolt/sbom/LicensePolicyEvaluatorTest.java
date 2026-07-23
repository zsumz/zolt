package sh.zolt.sbom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import sh.zolt.project.LicensePolicySettings;
import sh.zolt.project.UnknownLicensePolicy;

final class LicensePolicyEvaluatorTest {
    private final LicensePolicyEvaluator evaluator = new LicensePolicyEvaluator();

    @Test
    void denyAlwaysWinsEvenWhenAlsoAllowed() {
        LicensePolicyFinding finding = onlyFinding(
                spdx("GPL-3.0-only"),
                new LicensePolicySettings(List.of("GPL-3.0-only"), List.of("GPL-3.0-only"), UnknownLicensePolicy.WARN));
        assertEquals(LicenseVerdict.VIOLATION, finding.verdict());
        assertTrue(finding.reason().contains("deny"));
    }

    @Test
    void nonEmptyAllowListIsAuthoritative() {
        LicensePolicyFinding finding = onlyFinding(
                spdx("MIT"),
                new LicensePolicySettings(List.of("Apache-2.0"), List.of(), UnknownLicensePolicy.WARN));
        assertEquals(LicenseVerdict.VIOLATION, finding.verdict());
        assertTrue(finding.reason().contains("allow"));
    }

    @Test
    void permittedWhenNotDeniedAndAllowEmpty() {
        assertTrue(findings(spdx("MIT"), LicensePolicySettings.defaults()).isEmpty());
    }

    @Test
    void permittedWhenInAllowList() {
        assertTrue(findings(
                spdx("Apache-2.0"),
                new LicensePolicySettings(List.of("Apache-2.0"), List.of(), UnknownLicensePolicy.WARN)).isEmpty());
    }

    @Test
    void unmappedMatchesByRawStringInDeny() {
        LicensePolicyFinding finding = onlyFinding(
                unmapped("Weird License"),
                new LicensePolicySettings(List.of(), List.of("Weird License"), UnknownLicensePolicy.WARN));
        assertEquals(LicenseVerdict.VIOLATION, finding.verdict());
    }

    @Test
    void unmappedMatchesByRawStringInAllow() {
        assertTrue(findings(
                unmapped("Weird License"),
                new LicensePolicySettings(List.of("Weird License"), List.of(), UnknownLicensePolicy.WARN)).isEmpty());
    }

    @Test
    void unlistedUnmappedFollowsUnknownStrictness() {
        assertEquals(LicenseVerdict.WARN, onlyFinding(unmapped("Weird License"),
                policy(UnknownLicensePolicy.WARN)).verdict());
        assertEquals(LicenseVerdict.VIOLATION, onlyFinding(unmapped("Weird License"),
                policy(UnknownLicensePolicy.FAIL)).verdict());
        assertTrue(findings(unmapped("Weird License"), policy(UnknownLicensePolicy.ALLOW)).isEmpty());
    }

    @Test
    void unknownLicenseFollowsUnknownStrictnessMatrix() {
        assertEquals(LicenseVerdict.WARN, onlyFinding(unknown(), policy(UnknownLicensePolicy.WARN)).verdict());
        assertEquals(LicenseVerdict.VIOLATION, onlyFinding(unknown(), policy(UnknownLicensePolicy.FAIL)).verdict());
        assertTrue(findings(unknown(), policy(UnknownLicensePolicy.ALLOW)).isEmpty());
    }

    @Test
    void dualLicensePermittedWhenAnyOptionPermitted() {
        assertTrue(findings(
                List.of(SbomLicense.spdx("GPL-3.0-only"), SbomLicense.spdx("MIT")),
                new LicensePolicySettings(List.of(), List.of("GPL-3.0-only"), UnknownLicensePolicy.WARN)).isEmpty());
    }

    @Test
    void dualLicenseViolatesWhenEveryOptionViolates() {
        LicensePolicyFinding finding = onlyFinding(
                List.of(SbomLicense.spdx("GPL-3.0-only"), SbomLicense.spdx("AGPL-3.0-only")),
                new LicensePolicySettings(
                        List.of(), List.of("GPL-3.0-only", "AGPL-3.0-only"), UnknownLicensePolicy.WARN));
        assertEquals(LicenseVerdict.VIOLATION, finding.verdict());
    }

    private static LicensePolicySettings policy(UnknownLicensePolicy unknown) {
        return new LicensePolicySettings(List.of(), List.of(), unknown);
    }

    private List<LicensePolicyFinding> findings(SbomLicense license, LicensePolicySettings policy) {
        return findings(List.of(license), policy);
    }

    private List<LicensePolicyFinding> findings(List<SbomLicense> licenses, LicensePolicySettings policy) {
        String coordinate = "org.example:lib:1.0.0";
        LicenseIndex index = new LicenseIndex(Map.of(coordinate, licenses), List.of());
        return evaluator.evaluate(List.of(component()), index, policy);
    }

    private LicensePolicyFinding onlyFinding(SbomLicense license, LicensePolicySettings policy) {
        return onlyFinding(List.of(license), policy);
    }

    private LicensePolicyFinding onlyFinding(List<SbomLicense> licenses, LicensePolicySettings policy) {
        List<LicensePolicyFinding> findings = findings(licenses, policy);
        assertEquals(1, findings.size(), findings.toString());
        return findings.getFirst();
    }

    private static SbomComponent component() {
        String purl = PurlWriter.purl("org.example", "lib", "1.0.0", "jar", Optional.empty());
        return new SbomComponent(
                SbomComponentType.LIBRARY, purl, "org.example", "lib", "1.0.0", purl,
                SbomComponentScope.REQUIRED, List.of(), List.of());
    }

    private static SbomLicense spdx(String id) {
        return SbomLicense.spdx(id);
    }

    private static SbomLicense unmapped(String name) {
        return SbomLicense.unmapped(Optional.of(name), Optional.empty());
    }

    private static SbomLicense unknown() {
        return SbomLicense.unknown();
    }
}
