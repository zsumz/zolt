package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusArcBeanInputDiagnosticTest {
    @Test
    void reportsSelectedTestClassBeanItems() {
        String diagnostic = QuarkusArcBeanInputDiagnostic.formatTestClassBeanItems(
                List.of(
                        new FakeTestClassBeanBuildItem("com.example.ProfiledTest"),
                        new FakeTestClassBeanBuildItem("com.example.OtherTest")),
                List.of("com.example.ProfiledTest", "com.example.MissingTest"));

        assertEquals(
                "count=2,selected=com.example.ProfiledTest[items=1],com.example.MissingTest[items=0]",
                diagnostic);
    }

    @Test
    void reportsSelectedAdditionalBeanItems() {
        String diagnostic = QuarkusArcBeanInputDiagnostic.formatAdditionalBeanItems(
                List.of(
                        new FakeAdditionalBeanBuildItem(List.of("com.example.ProfiledTest"), false, null),
                        new FakeAdditionalBeanBuildItem(List.of("com.example.ProfiledTest"), true, "jakarta.inject.Singleton"),
                        new FakeAdditionalBeanBuildItem(List.of("com.example.OtherTest"), false, null)),
                List.of("com.example.ProfiledTest", "com.example.MissingTest"));

        assertEquals(
                "count=3,selected=com.example.ProfiledTest[items=2,unremovable=1,removable=1,defaultScopes=<none>|jakarta.inject.Singleton],com.example.MissingTest[items=0,unremovable=0,removable=0,defaultScopes=<none>]",
                diagnostic);
    }

    static final class FakeTestClassBeanBuildItem {
        private final String testClassName;

        FakeTestClassBeanBuildItem(String testClassName) {
            this.testClassName = testClassName;
        }

        public String getTestClassName() {
            return testClassName;
        }
    }

    static final class FakeAdditionalBeanBuildItem {
        private final List<String> beanClasses;
        private final boolean removable;
        private final String defaultScope;

        FakeAdditionalBeanBuildItem(
                List<String> beanClasses,
                boolean removable,
                String defaultScope) {
            this.beanClasses = beanClasses;
            this.removable = removable;
            this.defaultScope = defaultScope;
        }

        public List<String> getBeanClasses() {
            return beanClasses;
        }

        public boolean isRemovable() {
            return removable;
        }

        public String getDefaultScope() {
            return defaultScope;
        }
    }
}
