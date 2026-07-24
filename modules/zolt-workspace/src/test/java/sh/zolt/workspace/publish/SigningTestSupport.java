package sh.zolt.workspace.publish;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Shared throwaway-GPG helpers for publish tests that need real detached signatures: an isolated
 * {@code GNUPGHOME}, a generated signing key, and the environment lookup that feeds the passphrase and
 * home to {@code PublishSigner}. Every method degrades to a JUnit assumption when {@code gpg} is
 * unavailable so the suite stays green on machines without GnuPG.
 */
final class SigningTestSupport {
    static final String PASSPHRASE = "zolt-test-passphrase";

    private SigningTestSupport() {
    }

    static Function<String, String> signingEnvironment(Path gnupgHome) {
        Map<String, String> values = Map.of(
                "ZOLT_TEST_GPG_PASS", PASSPHRASE,
                "GNUPGHOME", gnupgHome.toString());
        return values::get;
    }

    static Path isolatedGnupgHome(Path tempDir) throws IOException {
        Path gnupgHome = tempDir.resolve("gnupg");
        Files.createDirectories(gnupgHome);
        try {
            Files.setPosixFilePermissions(gnupgHome, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystem; gpg still runs, just with a permissions warning.
        }
        return gnupgHome;
    }

    static void generateSigningKey(Path gnupgHome) throws IOException, InterruptedException {
        int exitCode = runGpg(gnupgHome, List.of(
                "--batch",
                "--pinentry-mode", "loopback",
                "--passphrase", PASSPHRASE,
                "--quick-generate-key", "Zolt Workspace Signing <signing@zolt.test>", "default", "sign", "0"));
        assumeTrue(exitCode == 0, "gpg could not generate a throwaway signing key");
    }

    static boolean gpgAvailable() {
        try {
            Process process = new ProcessBuilder("gpg", "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static int runGpg(Path gnupgHome, List<String> arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("gpg");
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("GNUPGHOME", gnupgHome.toString());
        Process process = builder.start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }
}
