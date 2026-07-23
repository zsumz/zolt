package sh.zolt.sbom;

/**
 * Which optional scope groups the caller opted into. {@link SbomScopeGroup#REQUIRED} is always
 * included; the four optional groups are gated by their {@code --include-*} flags.
 */
public record SbomScopeSelection(boolean provided, boolean dev, boolean test, boolean tools) {
    /** Compile + runtime only (the default). */
    public static SbomScopeSelection requiredOnly() {
        return new SbomScopeSelection(false, false, false, false);
    }

    public boolean includes(SbomScopeGroup group) {
        return switch (group) {
            case REQUIRED -> true;
            case PROVIDED -> provided;
            case DEV -> dev;
            case TEST -> test;
            case TOOLS -> tools;
        };
    }
}
