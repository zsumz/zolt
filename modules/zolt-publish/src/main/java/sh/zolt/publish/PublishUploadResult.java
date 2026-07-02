package sh.zolt.publish;

public record PublishUploadResult(PublishDryRunPlan plan) {
    public PublishUploadResult {
        if (plan == null) {
            throw new PublishException("Publish upload result requires a plan.");
        }
    }
}
