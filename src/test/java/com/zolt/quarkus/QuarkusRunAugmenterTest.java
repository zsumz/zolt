package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.framework.FrameworkRunException;
import org.junit.jupiter.api.Test;

final class QuarkusRunAugmenterTest {
    @Test
    void wrapsQuarkusAugmentationFailuresAsFrameworkRunFailures() {
        QuarkusRunAugmenter augmenter = new QuarkusRunAugmenter();

        FrameworkRunException exception = assertThrows(
                FrameworkRunException.class,
                () -> augmenter.augmentIfEnabled(null, null, null));

        assertTrue(exception.getMessage().contains("Quarkus build augmentation requires a project directory."));
        assertInstanceOf(QuarkusAugmentationException.class, exception.getCause());
    }
}
