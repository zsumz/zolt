package sh.zolt.build.generatedsource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import sh.zolt.build.BuildException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * OS-name is injected so the Windows PATHEXT-suffix resolution is verifiable on a POSIX host: files
 * are created on the real (macOS/Linux) filesystem and located under a simulated Windows os.name.
 */
final class ProcessToolLocatorTest {
    @TempDir
    private Path pathDir;

    @Test
    void resolvesWindowsExecutableSuffixOnPath() throws IOException {
        Path tool = executable("protoc.exe");

        Path resolved = ProcessToolLocator.locate(
                "protoc", pathDir.toString(), File.pathSeparator, "[generated.sources.gen]", "Windows 11");

        assertEquals(tool.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void resolvesWindowsCmdScriptWhenNoExePresent() throws IOException {
        Path tool = executable("buf.cmd");

        Path resolved = ProcessToolLocator.locate(
                "buf", pathDir.toString(), File.pathSeparator, "[generated.sources.gen]", "Windows 11");

        assertEquals(tool.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void doesNotAppendSuffixWhenBinaryAlreadyHasExecutableExtension() throws IOException {
        Path tool = executable("protoc.exe");

        Path resolved = ProcessToolLocator.locate(
                "protoc.exe", pathDir.toString(), File.pathSeparator, "[generated.sources.gen]", "Windows 11");

        assertEquals(tool.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void posixResolvesBareBinaryWithoutSuffix() throws IOException {
        Path tool = executable("protoc");

        Path resolved = ProcessToolLocator.locate(
                "protoc", pathDir.toString(), File.pathSeparator, "[generated.sources.gen]", "Linux");

        assertEquals(tool.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void posixDoesNotResolveWindowsSuffixedBinary() throws IOException {
        executable("protoc.exe");

        assertThrows(BuildException.class, () -> ProcessToolLocator.locate(
                "protoc", pathDir.toString(), File.pathSeparator, "[generated.sources.gen]", "Linux"));
    }

    private Path executable(String name) throws IOException {
        Path file = pathDir.resolve(name);
        Files.writeString(file, "#!/bin/sh\n");
        file.toFile().setExecutable(true);
        return file;
    }
}
