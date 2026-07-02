package sh.zolt.build.generatedsource;

import static sh.zolt.build.generatedsource.OpenApiGeneratedSourceServiceTestSupport.configWithAdditionalProperties;
import static sh.zolt.build.generatedsource.OpenApiGeneratedSourceServiceTestSupport.packages;
import static sh.zolt.build.generatedsource.OpenApiGeneratedSourceServiceTestSupport.service;
import static sh.zolt.build.generatedsource.OpenApiGeneratedSourceServiceTestSupport.writeProjectFiles;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.build.BuildException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OpenApiGeneratedSourceFailureTest {
    @TempDir
    private Path projectDir;

    @Test
    void generatorFailureIsActionableAndPreservesLog() throws IOException {
        writeProjectFiles(projectDir);
        OpenApiGeneratedSourceService service = service(projectDir, (command, directory) ->
                new OpenApiGeneratedSourceService.ProcessResult(17, "bad spec\n"));

        BuildException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BuildException.class,
                () -> service.generateMain(projectDir, configWithAdditionalProperties(Map.of("sourceFolder", ".")), packages(projectDir)));

        assertTrue(exception.getMessage().contains("OpenAPI generation failed for [generated.main.public-api]"));
        assertTrue(exception.getMessage().contains("exit code 17"));
        assertTrue(exception.getMessage().contains("bad spec"));
        assertTrue(Files.readString(projectDir.resolve(
                "target/generated/sources/openapi/public-api/.zolt-openapi-main-public-api.log")).contains("bad spec"));
    }
}
