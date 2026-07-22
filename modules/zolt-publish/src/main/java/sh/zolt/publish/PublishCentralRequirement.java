package sh.zolt.publish;

/**
 * One Maven Central publication requirement and whether the current project satisfies it.
 * {@code remediation} is an actionable next step shown when {@code satisfied} is false.
 */
public record PublishCentralRequirement(String name, boolean satisfied, String remediation) {
    public PublishCentralRequirement {
        name = name == null ? "" : name;
        remediation = remediation == null ? "" : remediation;
    }

    static PublishCentralRequirement satisfied(String name) {
        return new PublishCentralRequirement(name, true, "");
    }

    static PublishCentralRequirement unsatisfied(String name, String remediation) {
        return new PublishCentralRequirement(name, false, remediation);
    }
}
