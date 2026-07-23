package sh.zolt.build.incremental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.build.incremental.GeneratedOutputAttributionResolver.Resolution;
import sh.zolt.build.incremental.GeneratedOutputAttributionResolver.SourceGenerated;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GeneratedOutputAttributionResolverTest {
    private final GeneratedOutputAttributionResolver resolver = new GeneratedOutputAttributionResolver();
    private final Path widget = Path.of("/p/src/com/example/Widget.java").toAbsolutePath().normalize();
    private final Path root = Path.of("/p/src/com/example/Root.java").toAbsolutePath().normalize();

    @Test
    void resolvesAGeneratedSourceToItsHandwrittenOriginatingType() {
        GeneratedOutputAttribution.Entry entry = source("/gen/com/example/WidgetMeta.java", "com.example.WidgetMeta", "com.example.Widget");

        Resolution resolution = resolver.resolve(List.of(entry), Map.of("com.example.Widget", widget));

        assertTrue(resolution.complete());
        SourceGenerated generated = resolution.bySource().get(widget);
        assertEquals(Set.of(entry.path()), generated.generatedSourceFiles());
        assertEquals(Set.of("com.example.WidgetMeta"), generated.generatedTypes());
    }

    @Test
    void resolvesGeneratedFromGeneratedChainsToTheHandwrittenRoot() {
        GeneratedOutputAttribution.Entry first = source("/gen/A.java", "gen.A", "com.example.Root");
        GeneratedOutputAttribution.Entry second = source("/gen/B.java", "gen.B", "gen.A");

        Resolution resolution = resolver.resolve(List.of(first, second), Map.of("com.example.Root", root));

        assertTrue(resolution.complete());
        SourceGenerated generated = resolution.bySource().get(root);
        assertEquals(Set.of(first.path(), second.path()), generated.generatedSourceFiles());
        assertEquals(Set.of("gen.A", "gen.B"), generated.generatedTypes());
    }

    @Test
    void marksOutputWithNoOriginatingElementIncomplete() {
        GeneratedOutputAttribution.Entry entry = source("/gen/X.java", "gen.X");

        assertFalse(resolver.resolve(List.of(entry), Map.of("com.example.Widget", widget)).complete());
    }

    @Test
    void marksOutputWithMultipleDistinctRootsIncomplete() {
        GeneratedOutputAttribution.Entry entry = source(
                "/gen/Y.java", "gen.Y", "com.example.Widget", "com.example.Root");

        Resolution resolution = resolver.resolve(
                List.of(entry), Map.of("com.example.Widget", widget, "com.example.Root", root));

        assertFalse(resolution.complete());
    }

    @Test
    void marksUnresolvableOriginatingTypeIncomplete() {
        GeneratedOutputAttribution.Entry entry = source("/gen/Z.java", "gen.Z", "com.example.Unknown");

        assertFalse(resolver.resolve(List.of(entry), Map.of("com.example.Widget", widget)).complete());
    }

    @Test
    void resolvesAGeneratedResourceToItsHandwrittenOriginatingType() {
        GeneratedOutputAttribution.Entry entry = new GeneratedOutputAttribution.Entry(
                Path.of("/gen/service.txt"), GeneratedOutputAttribution.KIND_RESOURCE, "", List.of("com.example.Widget"));

        Resolution resolution = resolver.resolve(List.of(entry), Map.of("com.example.Widget", widget));

        assertTrue(resolution.complete());
        SourceGenerated generated = resolution.bySource().get(widget);
        assertEquals(Set.of(entry.path()), generated.generatedResourceFiles());
        assertTrue(generated.generatedSourceFiles().isEmpty());
        assertTrue(generated.generatedTypes().isEmpty());
    }

    @Test
    void marksGeneratedResourceWithoutOriginatingElementIncomplete() {
        GeneratedOutputAttribution.Entry entry = new GeneratedOutputAttribution.Entry(
                Path.of("/gen/service.txt"), GeneratedOutputAttribution.KIND_RESOURCE, "", List.of());

        assertFalse(resolver.resolve(List.of(entry), Map.of("com.example.Widget", widget)).complete());
    }

    @Test
    void marksGeneratedResourceWithMultipleDistinctRootsIncomplete() {
        GeneratedOutputAttribution.Entry entry = new GeneratedOutputAttribution.Entry(
                Path.of("/gen/service.txt"),
                GeneratedOutputAttribution.KIND_RESOURCE,
                "",
                List.of("com.example.Widget", "com.example.Root"));

        Resolution resolution = resolver.resolve(
                List.of(entry), Map.of("com.example.Widget", widget, "com.example.Root", root));

        assertFalse(resolution.complete());
    }

    private static GeneratedOutputAttribution.Entry source(String path, String createdType, String... originating) {
        return new GeneratedOutputAttribution.Entry(
                Path.of(path), GeneratedOutputAttribution.KIND_SOURCE, createdType, List.of(originating));
    }
}
