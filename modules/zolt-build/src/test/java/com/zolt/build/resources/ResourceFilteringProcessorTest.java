package com.zolt.build.resources;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.ProjectMetadata;
import com.zolt.project.ResourceFilteringSettings;
import com.zolt.project.ResourceMissingTokenPolicy;
import com.zolt.project.ResourceTokenSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResourceFilteringProcessorTest {
    @TempDir
    private Path tempDir;

    @Test
    void filtersSelectedTextResourceWithLiteralAndProjectTokens() throws IOException {
        Path resource = tempDir.resolve("application.properties");
        Files.writeString(resource, "name=@projectName@\ngreeting=@greeting@\n");
        ResourceFilteringSettings filtering = new ResourceFilteringSettings(
                true,
                false,
                List.of("**/*.properties"),
                ResourceMissingTokenPolicy.FAIL,
                Map.of(
                        "projectName", ResourceTokenSettings.project("name"),
                        "greeting", ResourceTokenSettings.literal("hello")));
        ResourceFilteringProcessor processor = ResourceFilteringProcessor.create(
                true,
                filtering,
                Optional.of(new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.empty())));

        byte[] filtered = processor.filteredBytes(resource, Path.of("application.properties")).orElseThrow();

        assertArrayEquals("name=demo\ngreeting=hello\n".getBytes(StandardCharsets.UTF_8), filtered);
    }

    @Test
    void leavesUnselectedResourceUnfiltered() throws IOException {
        Path resource = tempDir.resolve("image.txt");
        Files.writeString(resource, "name=@projectName@\n");
        ResourceFilteringSettings filtering = new ResourceFilteringSettings(
                true,
                false,
                List.of("**/*.properties"),
                ResourceMissingTokenPolicy.FAIL,
                Map.of("projectName", ResourceTokenSettings.project("name")));
        ResourceFilteringProcessor processor = ResourceFilteringProcessor.create(
                true,
                filtering,
                Optional.of(new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.empty())));

        assertTrue(processor.filteredBytes(resource, Path.of("image.txt")).isEmpty());
    }
}
