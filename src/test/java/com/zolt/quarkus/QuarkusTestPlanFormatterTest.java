package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusTestPlanFormatterTest {
    @Test
    void formatsPlainJUnitPlan() {
        QuarkusTestPlan plan = new QuarkusTestPlan(
                Path.of("/repo"),
                Path.of("/repo/target/test-classes"),
                false,
                Path.of("/repo/target/quarkus/test-application-model.dat"),
                List.of());

        assertEquals("""
                Quarkus test plan
                Status: plain JUnit tests can run through the current Zolt test runner
                Test output: /repo/target/test-classes
                Compiled test output: missing
                Serialized application model: /repo/target/quarkus/test-application-model.dat
                Unsupported Quarkus tests: 0
                Next: run `zolt test` for plain JUnit coverage; dedicated Quarkus test bootstrap remains future work.
                """, new QuarkusTestPlanFormatter().format(plan));
    }

    @Test
    void formatsBlockedPlan() {
        QuarkusTestPlan plan = new QuarkusTestPlan(
                Path.of("/repo"),
                Path.of("/repo/target/test-classes"),
                true,
                Path.of("/repo/target/quarkus/test-application-model.dat"),
                List.of(new QuarkusUnsupportedTest(
                        Path.of("/repo/target/test-classes/com/example/HttpTest.class"),
                        Path.of("com/example/HttpTest.class"),
                        "@QuarkusTest")));

        assertEquals("""
                Quarkus test plan
                Status: blocked by unsupported Quarkus test annotations
                Test output: /repo/target/test-classes
                Compiled test output: present
                Serialized application model: /repo/target/quarkus/test-application-model.dat
                Unsupported Quarkus tests: 1
                  com/example/HttpTest.class (@QuarkusTest)
                Next: remove unsupported Quarkus test annotations or wait for Zolt's dedicated Quarkus test runner.
                """, new QuarkusTestPlanFormatter().format(plan));
    }
}
