package sh.zolt.build.incremental;

public record IncrementalCompileWaveResult(
        IncrementalCompileValidation validation,
        int dependentSourceCount,
        String dependentOutput) {
    public IncrementalCompileWaveResult {
        dependentSourceCount = Math.max(0, dependentSourceCount);
        dependentOutput = dependentOutput == null ? "" : dependentOutput;
    }

    public boolean hasFallback() {
        return validation.hasFallback();
    }
}
