package com.zolt.quarkus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;

final class QuarkusTestProfileDiagnostic {
    private static final DotName QUARKUS_TEST = DotName.createSimple("io.quarkus.test.junit.QuarkusTest");
    private static final DotName TEST_PROFILE = DotName.createSimple("io.quarkus.test.junit.TestProfile");

    private QuarkusTestProfileDiagnostic() {
    }

    static List<String> testClasses(Index index) {
        if (index == null) {
            return List.of();
        }
        List<String> testClasses = new ArrayList<>();
        for (AnnotationInstance annotation : index.getAnnotations(QUARKUS_TEST)) {
            AnnotationTarget target = annotation.target();
            if (target.kind() == AnnotationTarget.Kind.CLASS && !target.asClass().isAnnotation()) {
                testClasses.add(target.asClass().name().toString());
            }
        }
        return testClasses.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    static List<TestClassProfile> testClassProfiles(Index index) {
        if (index == null) {
            return List.of();
        }
        List<TestClassProfile> profiles = new ArrayList<>();
        for (AnnotationInstance annotation : index.getAnnotations(QUARKUS_TEST)) {
            AnnotationTarget target = annotation.target();
            if (target.kind() == AnnotationTarget.Kind.CLASS && !target.asClass().isAnnotation()) {
                profiles.add(new TestClassProfile(
                        target.asClass().name().toString(),
                        testProfileClass(target.asClass().declaredAnnotation(TEST_PROFILE))));
            }
        }
        return profiles.stream()
                .distinct()
                .sorted(Comparator.comparing(TestClassProfile::testClass))
                .toList();
    }

    static String joinedProfiles(List<TestClassProfile> profiles) {
        if (profiles.isEmpty()) {
            return "<none>";
        }
        return profiles.stream()
                .map(profile -> profile.testClass()
                        + "=@TestProfile("
                        + profile.profileClass()
                        + ")")
                .collect(java.util.stream.Collectors.joining(","));
    }

    static String joinedProfileMatches(List<TestClassProfile> profiles, String activeProfile) {
        if (profiles.isEmpty()) {
            return "<none>";
        }
        return profiles.stream()
                .map(profile -> profile.testClass()
                        + "="
                        + profile.matches(activeProfile))
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String testProfileClass(AnnotationInstance annotation) {
        if (annotation == null) {
            return "<none>";
        }
        try {
            return annotation.value().asClass().name().toString();
        } catch (RuntimeException exception) {
            return "<unavailable:" + exception.getClass().getSimpleName() + ">";
        }
    }

    record TestClassProfile(String testClass, String profileClass) {
        private boolean matches(String activeProfile) {
            if ("<none>".equals(profileClass)) {
                return "<none>".equals(activeProfile);
            }
            return profileClass.equals(activeProfile);
        }
    }
}
