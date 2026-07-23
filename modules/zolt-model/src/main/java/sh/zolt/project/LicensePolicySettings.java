package sh.zolt.project;

import java.util.List;

/**
 * The {@code [dependencyPolicy.licenses]} gate configuration.
 *
 * <p>Precedence rule: a license is permitted iff its id is not in {@code deny} AND ({@code allow} is
 * empty OR its id is in {@code allow}) — deny always wins, and a non-empty allow-list is
 * authoritative. {@code unknown} controls the treatment of UNKNOWN-license dependencies.
 */
public record LicensePolicySettings(List<String> allow, List<String> deny, UnknownLicensePolicy unknown) {
    public LicensePolicySettings {
        allow = allow == null ? List.of() : List.copyOf(allow);
        deny = deny == null ? List.of() : List.copyOf(deny);
        unknown = unknown == null ? UnknownLicensePolicy.WARN : unknown;
    }

    public static LicensePolicySettings defaults() {
        return new LicensePolicySettings(List.of(), List.of(), UnknownLicensePolicy.WARN);
    }

    /** True when nothing is configured — no allow/deny entries and the default unknown strictness. */
    public boolean isDefault() {
        return allow.isEmpty() && deny.isEmpty() && unknown == UnknownLicensePolicy.WARN;
    }
}
