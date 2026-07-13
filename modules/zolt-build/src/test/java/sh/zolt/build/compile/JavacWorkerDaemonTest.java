package sh.zolt.build.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavacWorkerDaemonTest {
    @TempDir
    Path tempDir;

    @Test
    void stateIdentityTracksCompilerAndWorkerArtifact() throws Exception {
        Path firstJavac = tempDir.resolve("jdk-one/bin/javac");
        Path secondJavac = tempDir.resolve("jdk-two/bin/javac");
        Files.createDirectories(firstJavac.getParent());
        Files.createDirectories(secondJavac.getParent());
        Files.writeString(firstJavac, "first");
        Files.writeString(secondJavac, "second");
        Path worker = tempDir.resolve("zolt-javac-worker.jar");
        Files.writeString(worker, "worker-one");
        Path runtime = tempDir.resolve("runtime");

        Path first = JavacWorkerDaemon.statePath(firstJavac, worker, runtime);
        assertEquals(first, JavacWorkerDaemon.statePath(firstJavac, worker, runtime));
        assertNotEquals(first, JavacWorkerDaemon.statePath(secondJavac, worker, runtime));

        Files.writeString(worker, "worker-two-with-different-size");
        assertNotEquals(first, JavacWorkerDaemon.statePath(firstJavac, worker, runtime));
    }
}
