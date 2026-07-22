package sh.zolt.explain.verify;

/**
 * Workspace-wide totals across every module and scope. Counts are of resolved artifacts (drift counts
 * a single identity once) so they add up to an auditable whole.
 */
public record VerifySummary(
        int modules,
        int modulesBoth,
        int modulesMavenOnly,
        int modulesZoltOnly,
        int matched,
        int versionDrift,
        int onlyInMaven,
        int onlyInZolt) {

    public boolean hasDifferences() {
        return modulesMavenOnly > 0
                || modulesZoltOnly > 0
                || versionDrift > 0
                || onlyInMaven > 0
                || onlyInZolt > 0;
    }
}
