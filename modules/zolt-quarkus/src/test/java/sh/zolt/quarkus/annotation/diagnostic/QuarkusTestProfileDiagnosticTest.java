package sh.zolt.quarkus.annotation.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import sh.zolt.quarkus.testsupport.QuarkusJandexFixtureCompiler;
import sh.zolt.quarkus.testsupport.QuarkusJandexFixtureCompiler.FixtureRuntime;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusTestProfileDiagnosticTest {
    @Test
    void extractsTestClassesAndProfilesInDeterministicOrder(@TempDir Path tempDir) throws Exception {
        Path testOutputDirectory = QuarkusJandexFixtureCompiler.compile(
                tempDir.resolve("test-classes"),
                Map.of(
                        "com.example.BlueProfile", """
                                package com.example;

                                public class BlueProfile implements io.quarkus.test.junit.QuarkusTestProfile {
                                }
                                """,
                        "com.example.ZetaQuarkusCase", """
                                package com.example;

                                @io.quarkus.test.junit.QuarkusTest
                                public class ZetaQuarkusCase {
                                }
                                """,
                        "com.example.AlphaQuarkusCase", """
                                package com.example;

                                @io.quarkus.test.junit.QuarkusTest
                                @io.quarkus.test.junit.TestProfile(BlueProfile.class)
                                public class AlphaQuarkusCase {
                                }
                                """,
                        "com.example.QuarkusMarker", """
                                package com.example;

                                @io.quarkus.test.junit.QuarkusTest
                                public @interface QuarkusMarker {
                                }
                                """));

        try (FixtureRuntime runtime = QuarkusJandexFixtureCompiler.fixtureRuntime()) {
            Object index = runtime.index(testOutputDirectory);
            List<?> profiles = testClassProfiles(runtime, index);

            assertEquals(
                    List.of("com.example.AlphaQuarkusCase", "com.example.ZetaQuarkusCase"),
                    testClasses(runtime, index));
            assertEquals(
                    "[TestClassProfile[testClass=com.example.AlphaQuarkusCase, profileClass=com.example.BlueProfile], "
                            + "TestClassProfile[testClass=com.example.ZetaQuarkusCase, profileClass=<none>]]",
                    profiles.toString());
            assertEquals(
                    "com.example.AlphaQuarkusCase=@TestProfile(com.example.BlueProfile),"
                            + "com.example.ZetaQuarkusCase=@TestProfile(<none>)",
                    joinedProfiles(runtime, profiles));
            assertEquals(
                    "com.example.AlphaQuarkusCase=true,com.example.ZetaQuarkusCase=false",
                    joinedProfileMatches(runtime, profiles, "com.example.BlueProfile"));
            assertEquals(
                    "com.example.AlphaQuarkusCase=false,com.example.ZetaQuarkusCase=true",
                    joinedProfileMatches(runtime, profiles, "<none>"));
        }
    }

    @Test
    void formatsEmptyProfileLists() throws Exception {
        try (FixtureRuntime runtime = QuarkusJandexFixtureCompiler.fixtureRuntime()) {
            assertEquals("<none>", joinedProfiles(runtime, List.of()));
            assertEquals("<none>", joinedProfileMatches(runtime, List.of(), "<none>"));
        }
    }

    private static List<?> testClasses(FixtureRuntime runtime, Object index) throws Exception {
        return (List<?>) diagnosticMethod(runtime, "testClasses", runtime.loadClass("org.jboss.jandex.Index"))
                .invoke(null, index);
    }

    private static List<?> testClassProfiles(FixtureRuntime runtime, Object index) throws Exception {
        return (List<?>) diagnosticMethod(runtime, "testClassProfiles", runtime.loadClass("org.jboss.jandex.Index"))
                .invoke(null, index);
    }

    private static String joinedProfiles(FixtureRuntime runtime, List<?> profiles) throws Exception {
        return (String) diagnosticMethod(runtime, "joinedProfiles", List.class).invoke(null, profiles);
    }

    private static String joinedProfileMatches(FixtureRuntime runtime, List<?> profiles, String activeProfile)
            throws Exception {
        return (String) diagnosticMethod(runtime, "joinedProfileMatches", List.class, String.class)
                .invoke(null, profiles, activeProfile);
    }

    private static Method diagnosticMethod(FixtureRuntime runtime, String name, Class<?>... parameterTypes)
            throws Exception {
        return runtime.loadClass("sh.zolt.quarkus.annotation.diagnostic.QuarkusTestProfileDiagnostic")
                .getMethod(name, parameterTypes);
    }
}
