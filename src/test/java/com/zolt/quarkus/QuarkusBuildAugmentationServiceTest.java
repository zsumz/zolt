package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class QuarkusBuildAugmentationServiceTest extends QuarkusBuildAugmentationServiceTestSupport {
    @Test
    void skipsAugmentationWhenQuarkusIsDisabled() {
        boolean[] planned = new boolean[] {false};
        boolean[] ran = new boolean[] {false};
        QuarkusBuildAugmentationService service = new QuarkusBuildAugmentationService(
                (projectDirectory, config, cacheRoot) -> {
                    planned[0] = true;
                    return plan();
                },
                plan -> request(),
                (config, request) -> {
                    ran[0] = true;
                    return result(request);
                });

        Optional<QuarkusAugmentationResult> result = service.augmentIfEnabled(
                Path.of("/repo"),
                config(false),
                Path.of("/cache"));

        assertTrue(result.isEmpty());
        assertFalse(planned[0]);
        assertFalse(ran[0]);
    }

    @Test
    void runsAugmentationForQuarkusEnabledProject() {
        Path projectDirectory = Path.of("/repo");
        Path cacheRoot = Path.of("/cache");
        var config = config(true);
        var plan = plan();
        var request = request();
        var expected = result(request);
        QuarkusBuildAugmentationService service = new QuarkusBuildAugmentationService(
                (actualProjectDirectory, actualConfig, actualCacheRoot) -> {
                    assertEquals(projectDirectory, actualProjectDirectory);
                    assertSame(config, actualConfig);
                    assertEquals(cacheRoot, actualCacheRoot);
                    return plan;
                },
                actualPlan -> {
                    assertSame(plan, actualPlan);
                    return request;
                },
                (actualConfig, actualRequest) -> {
                    assertSame(config, actualConfig);
                    assertSame(request, actualRequest);
                    return expected;
                });

        Optional<QuarkusAugmentationResult> result = service.augmentIfEnabled(projectDirectory, config, cacheRoot);

        assertTrue(result.isPresent());
        assertSame(expected, result.orElseThrow());
    }
}
