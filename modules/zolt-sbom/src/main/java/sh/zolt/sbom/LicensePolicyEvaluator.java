package sh.zolt.sbom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import sh.zolt.project.LicensePolicySettings;

/**
 * Evaluates resolved dependency licenses against {@link LicensePolicySettings}.
 *
 * <p>Precedence for a single license: permitted iff its id is not in {@code deny} AND ({@code allow}
 * is empty OR its id is in {@code allow}). An UNMAPPED license matches by its raw string; if it is
 * neither explicitly denied nor allowed it follows the {@code unknown} strictness, as does an UNKNOWN
 * license. A dependency with multiple (dual) licenses takes its most permissive per-license verdict —
 * you may satisfy the policy with any one of the offered licenses.
 */
public final class LicensePolicyEvaluator {
    /** Findings for every dependency that is not outright permitted, sorted by coordinate. */
    public List<LicensePolicyFinding> evaluate(
            List<SbomComponent> components,
            LicenseIndex index,
            LicensePolicySettings policy) {
        List<LicensePolicyFinding> findings = new ArrayList<>();
        for (SbomComponent component : components) {
            String coordinate = component.group() + ":" + component.name() + ":" + component.version();
            List<SbomLicense> licenses = index.forCoordinate(coordinate);
            if (licenses.isEmpty()) {
                licenses = List.of(SbomLicense.unknown());
            }
            dependencyFinding(coordinate, component.purl(), licenses, policy).ifPresent(findings::add);
        }
        findings.sort(Comparator.comparing(LicensePolicyFinding::coordinate));
        return List.copyOf(findings);
    }

    private Optional<LicensePolicyFinding> dependencyFinding(
            String coordinate,
            String purl,
            List<SbomLicense> licenses,
            LicensePolicySettings policy) {
        LicensePolicyFinding best = null;
        for (SbomLicense license : licenses) {
            LicensePolicyFinding candidate = evaluateLicense(coordinate, purl, license, policy);
            if (candidate.verdict() == LicenseVerdict.PERMITTED) {
                return Optional.empty();
            }
            if (best == null || candidate.verdict().ordinal() < best.verdict().ordinal()) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private LicensePolicyFinding evaluateLicense(
            String coordinate,
            String purl,
            SbomLicense license,
            LicensePolicySettings policy) {
        return switch (license.status()) {
            case SPDX -> spdxVerdict(coordinate, purl, license.spdxId().orElseThrow(), policy);
            case UNMAPPED -> unmappedVerdict(coordinate, purl, license.label(), policy);
            case UNKNOWN -> unknownVerdict(coordinate, purl, "UNKNOWN", policy);
        };
    }

    private LicensePolicyFinding spdxVerdict(String coordinate, String purl, String id, LicensePolicySettings policy) {
        if (policy.deny().contains(id)) {
            return finding(coordinate, purl, id, LicenseVerdict.VIOLATION,
                    "denied by [dependencyPolicy.licenses].deny");
        }
        if (!policy.allow().isEmpty() && !policy.allow().contains(id)) {
            return finding(coordinate, purl, id, LicenseVerdict.VIOLATION,
                    "not in [dependencyPolicy.licenses].allow");
        }
        return finding(coordinate, purl, id, LicenseVerdict.PERMITTED, "");
    }

    private LicensePolicyFinding unmappedVerdict(String coordinate, String purl, String raw, LicensePolicySettings policy) {
        if (policy.deny().contains(raw)) {
            return finding(coordinate, purl, raw, LicenseVerdict.VIOLATION,
                    "denied by [dependencyPolicy.licenses].deny");
        }
        if (policy.allow().contains(raw)) {
            return finding(coordinate, purl, raw, LicenseVerdict.PERMITTED, "");
        }
        return unknownVerdict(coordinate, purl, raw, policy);
    }

    private LicensePolicyFinding unknownVerdict(String coordinate, String purl, String label, LicensePolicySettings policy) {
        return switch (policy.unknown()) {
            case FAIL -> finding(coordinate, purl, label, LicenseVerdict.VIOLATION,
                    "unrecognized license and [dependencyPolicy.licenses].unknown = fail");
            case WARN -> finding(coordinate, purl, label, LicenseVerdict.WARN,
                    "unrecognized license ([dependencyPolicy.licenses].unknown = warn)");
            case ALLOW -> finding(coordinate, purl, label, LicenseVerdict.PERMITTED, "");
        };
    }

    private static LicensePolicyFinding finding(
            String coordinate, String purl, String license, LicenseVerdict verdict, String reason) {
        return new LicensePolicyFinding(coordinate, purl, license, verdict, reason);
    }
}
