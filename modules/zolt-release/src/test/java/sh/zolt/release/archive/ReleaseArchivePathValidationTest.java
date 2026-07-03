package sh.zolt.release.archive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.release.ReleaseTarget;
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
    void rejectsAbsoluteReleaseBinarySymlinkThatEscapesProject() throws IOException {
        Path outside = Files.createTempFile(projectDir.getParent(), "outside-binary-", "");
        Files.writeString(outside, "native");
        Path binary = projectDir.resolve("target/native/zolt");
        Files.createDirectories(binary.getParent());
        createSymlink(binary, outside);

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        binary.toAbsolutePath().normalize(),
                        Path.of("dist")));

        assertTrue(exception.getMessage().contains("--binary"));
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
        assertTrue(exception.getMessage().contains("outside-binary"));
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

    @Test
    void rejectsAbsoluteReleaseOutputSymlinkThatEscapesProject() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");
        Path outside = Files.createTempDirectory(projectDir.getParent(), "outside-dist-");
        Path output = projectDir.resolve("dist");
        createSymlink(output, outside);

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        binary,
                        output.toAbsolutePath().normalize()));

        assertTrue(exception.getMessage().contains("--output"));
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
        assertFalse(Files.exists(outside.resolve("zolt-0.1.0-linux-x64.tar.gz")));
    }

    @Test
    void wrapsArchiveWriteFailureWithWritableOutputDiagnostic() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");
        Files.writeString(projectDir.resolve("dist"), "not a directory");

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        binary,
                        Path.of("dist")));

        assertTrue(exception.getMessage().contains("Could not write release archive"));
        assertTrue(exception.getMessage().contains("Check that the output directory is writable."));
    }
}
