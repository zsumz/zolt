package com.zolt.quarkus;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class QuarkusAnnotationRunnerCandidates {
    private static final String QUARKUS_TEST = "@QuarkusTest";

    private QuarkusAnnotationRunnerCandidates() {
    }

    static List<QuarkusUnsupportedTest> select(List<QuarkusUnsupportedTest> tests) {
        Map<Path, QuarkusUnsupportedTest> selected = new LinkedHashMap<>();
        for (QuarkusUnsupportedTest test : tests) {
            if (!test.annotationRunnerSupported()) {
                continue;
            }
            selected.merge(
                    test.relativePath(),
                    test,
                    QuarkusAnnotationRunnerCandidates::preferredCandidate);
        }
        return List.copyOf(selected.values());
    }

    private static QuarkusUnsupportedTest preferredCandidate(
            QuarkusUnsupportedTest current,
            QuarkusUnsupportedTest candidate) {
        if (!QUARKUS_TEST.equals(current.annotationName())
                && QUARKUS_TEST.equals(candidate.annotationName())) {
            return candidate;
        }
        return current;
    }
}
