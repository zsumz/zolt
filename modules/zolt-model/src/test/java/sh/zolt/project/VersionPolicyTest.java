package sh.zolt.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class VersionPolicyTest {
    @Test
    void externalDependencyVersionsRejectRangesDynamicSelectorsSnapshotsAndIncompleteLiterals() {
        assertRule(VersionPolicy.Context.EXTERNAL_DEPENDENCY, "[1.0,2.0)", "version-range");
        assertRule(VersionPolicy.Context.EXTERNAL_DEPENDENCY, "1.+", "dynamic-version");
        assertRule(VersionPolicy.Context.EXTERNAL_DEPENDENCY, "LATEST", "dynamic-version");
        assertRule(VersionPolicy.Context.EXTERNAL_DEPENDENCY, "RELEASE", "dynamic-version");
        assertRule(VersionPolicy.Context.EXTERNAL_DEPENDENCY, "1.0-SNAPSHOT", "snapshot-version");
        assertRule(VersionPolicy.Context.EXTERNAL_DEPENDENCY, "1.0.", "incomplete-version");
        assertRule(VersionPolicy.Context.EXTERNAL_DEPENDENCY, "$slf4jVersion", "no-interpolation");
        assertRule(VersionPolicy.Context.EXTERNAL_DEPENDENCY, "${gsonVersion}", "no-interpolation");
    }

    @Test
    void platformConstraintToolAndAliasVersionsUseTheSameExternalPolicyShape() {
        for (VersionPolicy.Context context : List.of(
                VersionPolicy.Context.VERSION_ALIAS,
                VersionPolicy.Context.PLATFORM,
                VersionPolicy.Context.CONSTRAINT,
                VersionPolicy.Context.TOOL_DEPENDENCY)) {
            assertRule(context, "[1.0,2.0)", "version-range");
            assertRule(context, "latest.release", "dynamic-version");
            assertRule(context, "1.0-SNAPSHOT", "snapshot-version");
        }
    }

    @Test
    void projectVersionsMayBeSnapshotsButNotDynamicSelectors() {
        assertTrue(VersionPolicy.isSupported(VersionPolicy.Context.PROJECT_VERSION, "0.1.0-SNAPSHOT"));
        assertRule(VersionPolicy.Context.PROJECT_VERSION, "latest.release", "dynamic-version");
    }

    @Test
    void publishContextsClassifyReleaseAndSnapshotVersions() {
        assertTrue(VersionPolicy.isSupported(VersionPolicy.Context.PUBLISH_RELEASE, "1.0.0"));
        assertRule(VersionPolicy.Context.PUBLISH_RELEASE, "1.0.0-SNAPSHOT", "snapshot-version");
        assertTrue(VersionPolicy.isSupported(VersionPolicy.Context.PUBLISH_SNAPSHOT, "1.0.0-SNAPSHOT"));
        assertRule(VersionPolicy.Context.PUBLISH_SNAPSHOT, "1.0.0", "snapshot-required");
        assertEquals("snapshot", VersionPolicy.classifyPublishVersion("1.0.0-SNAPSHOT"));
        assertEquals("release", VersionPolicy.classifyPublishVersion("1.0.0"));
    }

    private static void assertRule(VersionPolicy.Context context, String version, String rule) {
        assertEquals(rule, VersionPolicy.violation(context, version).orElseThrow().rule());
    }
}
