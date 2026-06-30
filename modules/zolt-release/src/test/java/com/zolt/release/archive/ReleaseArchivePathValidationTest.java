package com.zolt.release.archive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.release.ReleaseTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ReleaseArchivePathValidationTest extends ReleaseArchiveTestSupport {
    private final ReleaseArchiveService service = new ReleaseArchiveService();

    @Test
    void rejectsReleaseOutputOutsideProject() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        binary,
                        Path.of("../dist")));

        assertTrue(exception.getMessage().contains("--output"));
        assertTrue(exception.getMessage().contains("../dist"));
    }

    @Test
    void rejectsAbsoluteReleaseOutputOutsideProject() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");
        Path outside = Files.createTempDirectory(projectDir.getParent(), "outside-dist-");

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        binary,
                        outside.toAbsolutePath().normalize()));

        assertTrue(exception.getMessage().contains("--output"));
        assertTrue(exception.getMessage().contains("outside-dist"));
        assertTrue(exception.getMessage().contains("project-relative path or an absolute path under"));
    }

    @Test
    void rejectsReleaseBinaryOutsideProject() throws IOException {
        Path outside = Files.createTempFile(projectDir.getParent(), "outside-binary-", "");
        Files.writeString(outside, "native");

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        projectDir.relativize(outside),
                        Path.of("dist")));

        assertTrue(exception.getMessage().contains("--binary"));
        assertTrue(exception.getMessage().contains("outside-binary"));
    }

    @Test
    void rejectsAbsoluteReleaseBinaryOutsideProject() throws IOException {
        Path outside = Files.createTempFile(projectDir.getParent(), "outside-binary-", "");
        Files.writeString(outside, "native");

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        outside.toAbsolutePath().normalize(),
                        Path.of("dist")));

        assertTrue(exception.getMessage().contains("--binary"));
        assertTrue(exception.getMessage().contains("outside-binary"));
        assertTrue(exception.getMessage().contains("project-relative path or an absolute path under"));
    }

    @Test
    void rejectsReleaseOutputSymlinkThatEscapesProject() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");
        Path outside = Files.createTempDirectory(projectDir.getParent(), "outside-dist-");
        createSymlink(projectDir.resolve("dist"), outside);

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        binary,
                        Path.of("dist")));

        assertTrue(exception.getMessage().contains("--output"));
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
        assertFalse(Files.exists(outside.resolve("zolt-0.1.0-linux-x64.tar.gz")));
    }
}
