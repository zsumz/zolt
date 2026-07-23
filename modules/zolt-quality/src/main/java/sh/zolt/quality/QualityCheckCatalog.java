package sh.zolt.quality;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

final class QualityCheckCatalog {
    static final String COMMAND_SURFACE = "command-surface";
    static final String CACHE_INTEGRITY = "cache-integrity";
    static final String LOCKFILE = "lockfile";
    static final String PROJECT_MODEL = "project-model";
    static final String DEPENDENCY_METADATA = "dependency-metadata";
    static final String DEPENDENCY_POLICY = "dependency-policy";
    static final String LICENSE_POLICY = "license-policy";
    static final String PACKAGE_METADATA = "package-metadata";
    static final String PACKAGE_CONTENTS = "package-contents";
    static final String MANIFEST_METADATA = "manifest-metadata";
    static final String GENERATED_SOURCES = "generated-sources";
    static final String EXECUTION_CONTEXT = "execution-context";

    private static final Set<String> IMPLEMENTED_CHECKS = Set.of(
            COMMAND_SURFACE,
            CACHE_INTEGRITY,
            EXECUTION_CONTEXT,
            LOCKFILE,
            PROJECT_MODEL,
            DEPENDENCY_METADATA,
            DEPENDENCY_POLICY,
            LICENSE_POLICY,
            PACKAGE_METADATA,
            PACKAGE_CONTENTS,
            MANIFEST_METADATA,
            GENERATED_SOURCES);
    private static final List<String> CI_CONTEXT_CHECKS = List.of(
            EXECUTION_CONTEXT,
            LOCKFILE,
            PROJECT_MODEL,
            DEPENDENCY_METADATA,
            DEPENDENCY_POLICY,
            LICENSE_POLICY,
            GENERATED_SOURCES,
            PACKAGE_CONTENTS);
    private static final Map<String, String> PLANNED_CHECK_NOTES = Map.of();

    private QualityCheckCatalog() {
    }

    static Set<String> supportedChecks() {
        Set<String> supported = new LinkedHashSet<>(IMPLEMENTED_CHECKS);
        supported.addAll(new TreeMap<>(PLANNED_CHECK_NOTES).keySet());
        return Collections.unmodifiableSet(supported);
    }

    static List<String> requestedChecks(QualityCheckRequest request) {
        List<String> rawChecks = request.checks();
        if (rawChecks.isEmpty()) {
            if (request.context() == QualityCheckContext.CI) {
                return CI_CONTEXT_CHECKS;
            }
            if (request.context() == QualityCheckContext.LOCAL) {
                return List.of(EXECUTION_CONTEXT);
            }
            return List.of(COMMAND_SURFACE);
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (request.context() == QualityCheckContext.CI || request.context() == QualityCheckContext.LOCAL) {
            normalized.add(EXECUTION_CONTEXT);
        }
        for (String rawCheck : rawChecks) {
            String check = rawCheck == null ? "" : rawCheck.trim();
            if (!check.isEmpty()) {
                normalized.add(check);
            }
        }
        return List.copyOf(normalized);
    }

    static List<QualityCheckResult> unavailableResults(
            List<String> requestedChecks,
            String subject,
            String message,
            String nextStep) {
        List<QualityCheckResult> results = new ArrayList<>();
        for (String requestedCheck : requestedChecks) {
            if (IMPLEMENTED_CHECKS.contains(requestedCheck)) {
                results.add(QualityCheckResult.failed(
                        requestedCheck,
                        Optional.empty(),
                        subject,
                        message,
                        nextStep));
            } else {
                results.add(unsupportedOrSkipped(requestedCheck));
            }
        }
        return List.copyOf(results);
    }

    static QualityCheckResult unsupportedOrSkipped(String requestedCheck) {
        if (PLANNED_CHECK_NOTES.containsKey(requestedCheck)) {
            return QualityCheckResult.skipped(
                    requestedCheck,
                    Optional.empty(),
                    requestedCheck,
                    "Quality check `" + requestedCheck + "` is planned but not implemented yet.",
                    "Track " + PLANNED_CHECK_NOTES.get(requestedCheck) + ".");
        }
        return QualityCheckResult.failed(
                "unsupported-check",
                Optional.empty(),
                requestedCheck,
                "Unsupported quality check `" + requestedCheck + "`.",
                "Use one of: " + String.join(", ", supportedChecks()) + ". Zolt does not run Maven goals, Gradle tasks, shell commands, or arbitrary hooks.");
    }
}
