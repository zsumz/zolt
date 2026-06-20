package com.zolt.generated;

import com.zolt.project.OpenApiGenerationSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Optional;

final class GeneratedSourceEvidenceServiceTestSupport {
    private GeneratedSourceEvidenceServiceTestSupport() {
    }

    static Path write(Path tempDir, String relativePath, String content, long modifiedMillis) throws IOException {
        Path path = tempDir.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        Files.setLastModifiedTime(path, FileTime.fromMillis(modifiedMillis));
        return path;
    }

    static OpenApiGenerationSettings openApiSettings(String apiPackage, String modelPackage) {
        return new OpenApiGenerationSettings(
                Optional.of("org.openapitools:openapi-generator-cli"),
                Optional.of("7.11.0"),
                Optional.of("spring-api"),
                Optional.of("spring"),
                Optional.of("spring-boot"),
                Optional.of(apiPackage),
                Optional.of(modelPackage),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of("interfaceOnly", "true"),
                Map.of(),
                Map.of("useSpringBoot3", "true"),
                Map.of("models", "", "apis", ""),
                Map.of(),
                Map.of());
    }
}
