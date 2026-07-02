package sh.zolt.quality;

import java.util.Optional;

public record QualityCheckResult(
        String id,
        QualityCheckSeverity severity,
        QualityCheckStatus status,
        Optional<String> member,
        String subject,
        String message,
        String nextStep) {
    public QualityCheckResult {
        member = member == null ? Optional.empty() : member;
    }

    public static QualityCheckResult passed(String id, Optional<String> member, String subject, String message) {
        return new QualityCheckResult(id, QualityCheckSeverity.INFO, QualityCheckStatus.PASSED, member, subject, message, "");
    }

    public static QualityCheckResult failed(
            String id,
            Optional<String> member,
            String subject,
            String message,
            String nextStep) {
        return new QualityCheckResult(id, QualityCheckSeverity.ERROR, QualityCheckStatus.FAILED, member, subject, message, nextStep);
    }

    public static QualityCheckResult warning(
            String id,
            Optional<String> member,
            String subject,
            String message,
            String nextStep) {
        return new QualityCheckResult(id, QualityCheckSeverity.WARN, QualityCheckStatus.WARNING, member, subject, message, nextStep);
    }

    public static QualityCheckResult skipped(
            String id,
            Optional<String> member,
            String subject,
            String message,
            String nextStep) {
        return new QualityCheckResult(id, QualityCheckSeverity.INFO, QualityCheckStatus.SKIPPED, member, subject, message, nextStep);
    }
}
