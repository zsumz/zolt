package sh.zolt.project;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DependencyPolicySettingsTest {
    @Test
    void defaultsDoNotFailOnVersionConflicts() {
        assertFalse(DependencyPolicySettings.defaults().failOnVersionConflict());
    }

    @Test
    void twoArgumentConstructorKeepsFailOnVersionConflictDisabled() {
        DependencyPolicySettings settings = new DependencyPolicySettings(List.of(), Map.of());

        assertFalse(settings.failOnVersionConflict());
    }

    @Test
    void explicitFlagCanEnableFailOnVersionConflict() {
        DependencyPolicySettings settings = new DependencyPolicySettings(List.of(), Map.of(), true);

        assertTrue(settings.failOnVersionConflict());
    }
}
