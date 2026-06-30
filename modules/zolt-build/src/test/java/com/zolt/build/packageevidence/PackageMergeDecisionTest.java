package com.zolt.build.packageevidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.PackageException;
import com.zolt.build.packaging.PackageMergeDecision;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PackageMergeDecisionTest {
    @Test
    void acceptsSupportedDecisionKinds() {
        assertEquals("service-descriptor", decision("service-descriptor").kind());
        assertEquals("netty-version-metadata", decision("netty-version-metadata").kind());
        assertEquals("omitted-module-descriptor", decision("omitted-module-descriptor").kind());
        assertEquals(
                Optional.of("META-INF/zolt-uber/com/example/lib/1.0.0/LICENSE.txt"),
                new PackageMergeDecision(
                        "relocated-metadata",
                        "META-INF/LICENSE.txt",
                        Optional.of("META-INF/zolt-uber/com/example/lib/1.0.0/LICENSE.txt"),
                        List.of("com.example:lib")).target());
    }

    @Test
    void rejectsUnknownDecisionKinds() {
        PackageException exception = assertThrows(
                PackageException.class,
                () -> new PackageMergeDecision(
                        "custom-transformer",
                        "META-INF/custom",
                        Optional.empty(),
                        List.of("com.example:lib")));

        assertTrue(exception.getMessage().contains("Unsupported package merge decision kind `custom-transformer`"));
        assertTrue(exception.getMessage().contains("Regenerate package evidence with `zolt package`."));
    }

    @Test
    void relocatedMetadataRequiresTarget() {
        PackageException exception = assertThrows(
                PackageException.class,
                () -> new PackageMergeDecision(
                        "relocated-metadata",
                        "META-INF/LICENSE.txt",
                        Optional.empty(),
                        List.of("com.example:lib")));

        assertEquals("Relocated package merge decisions require a nonblank target path.", exception.getMessage());
    }

    @Test
    void nonRelocationDecisionRejectsTarget() {
        PackageException exception = assertThrows(
                PackageException.class,
                () -> new PackageMergeDecision(
                        "service-descriptor",
                        "META-INF/services/com.example.Plugin",
                        Optional.of("META-INF/other"),
                        List.of("com.example:lib")));

        assertEquals(
                "Package merge decision kind `service-descriptor` must not declare a target path.",
                exception.getMessage());
    }

    @Test
    void sourcesAreRequiredAndNonblank() {
        assertThrows(
                PackageException.class,
                () -> new PackageMergeDecision(
                        "service-descriptor",
                        "META-INF/services/com.example.Plugin",
                        Optional.empty(),
                        List.of()));
        PackageException blank = assertThrows(
                PackageException.class,
                () -> new PackageMergeDecision(
                        "service-descriptor",
                        "META-INF/services/com.example.Plugin",
                        Optional.empty(),
                        List.of(" ")));

        assertEquals("Package merge decision sources must not be blank.", blank.getMessage());
    }

    private static PackageMergeDecision decision(String kind) {
        return new PackageMergeDecision(
                kind,
                "META-INF/services/com.example.Plugin",
                Optional.empty(),
                List.of("com.example:lib"));
    }
}
