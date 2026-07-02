package sh.zolt.explain;

public record ExplainSignalDefinition(
        String id,
        ExplainSignal.Severity severity,
        ExplainSignal.Category category,
        String nextStep) {
    public ExplainSignalDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Explain signal id is required.");
        }
        if (severity == null) {
            throw new IllegalArgumentException("Explain signal severity is required.");
        }
        if (category == null) {
            throw new IllegalArgumentException("Explain signal category is required.");
        }
        if (nextStep == null || nextStep.isBlank()) {
            throw new IllegalArgumentException("Explain signal next step is required.");
        }
    }

    public ExplainSignal signal(String project, String message) {
        return new ExplainSignal(severity, category, project, id, message, nextStep);
    }
}
