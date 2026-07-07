package sh.zolt.release.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

final class NativeUpdateVerifier {
    private NativeUpdateVerifier() {
    }

    static void smokeCandidate(Path executable, String expectedVersion) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(executable.toString(), "--version")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        int exitCode = process.waitFor();
        if (exitCode != 0 || !output.equals(expectedVersion)) {
            throw new NativeUpdateException("Downloaded native Zolt failed smoke verification. Expected version " + expectedVersion + " but got `" + output + "`.");
        }
    }
}
