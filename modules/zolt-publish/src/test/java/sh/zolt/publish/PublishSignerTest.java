package sh.zolt.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PublishSignerTest {
    private static final String PASSPHRASE = "zolt-test-passphrase";

    @TempDir
    private Path tempDir;

    @Test
    void signsFileWithLoopbackPassphraseAndProducesVerifiableSignature() throws Exception {
        assumeTrue(gpgAvailable(), "gpg is not installed");
        Path gnupgHome = isolatedGnupgHome();
        generateSigningKey(gnupgHome);
        Path artifact = Files.writeString(tempDir.resolve("artifact.jar"), "zolt signing payload\n");

        PublishSigner signer = new PublishSigner(
                new PublishSigningSettings(true, Optional.empty(), Optional.of("ZOLT_TEST_GPG_PASS")),
                environment(gnupgHome));

        Path signature = signer.sign(artifact);

        assertEquals(artifact.resolveSibling("artifact.jar.asc"), signature);
        assertTrue(Files.isRegularFile(signature));
        assertTrue(Files.readString(signature).contains("-----BEGIN PGP SIGNATURE-----"));
        assertEquals(0, verify(gnupgHome, signature, artifact), "gpg should verify the detached signature");
    }

    @Test
    void missingPassphraseEnvironmentRaisesActionableError() {
        PublishSigner signer = new PublishSigner(
                new PublishSigningSettings(true, Optional.empty(), Optional.of("ZOLT_TEST_GPG_PASS")),
                name -> null);

        PublishException exception = assertThrows(
                PublishException.class,
                () -> signer.sign(tempDir.resolve("artifact.jar")));

        assertTrue(exception.getMessage().contains("ZOLT_TEST_GPG_PASS"));
        assertTrue(exception.getMessage().contains("Next:"));
    }

    @Test
    void missingGpgBinaryRaisesActionableError() throws IOException {
        Path artifact = Files.writeString(tempDir.resolve("artifact.jar"), "payload\n");
        PublishSigner signer = new PublishSigner(
                new PublishSigningSettings(true, Optional.empty(), Optional.empty()),
                name -> null,
                tempDir.resolve("no-such-gpg-binary").toString());

        PublishException exception = assertThrows(PublishException.class, () -> signer.sign(artifact));

        assertTrue(exception.getMessage().contains("Could not run gpg"));
        assertTrue(exception.getMessage().contains("install GnuPG"));
    }

    private Function<String, String> environment(Path gnupgHome) {
        Map<String, String> values = Map.of(
                "ZOLT_TEST_GPG_PASS", PASSPHRASE,
                "GNUPGHOME", gnupgHome.toString());
        return values::get;
    }

    private Path isolatedGnupgHome() throws IOException {
        Path gnupgHome = tempDir.resolve("gnupg");
        Files.createDirectories(gnupgHome);
        try {
            Files.setPosixFilePermissions(gnupgHome, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystem; gpg still runs, just with a permissions warning.
        }
        return gnupgHome;
    }

    private void generateSigningKey(Path gnupgHome) throws IOException, InterruptedException {
        int exitCode = runGpg(gnupgHome, List.of(
                "--batch",
                "--pinentry-mode", "loopback",
                "--passphrase", PASSPHRASE,
                "--quick-generate-key", "Zolt Signing Test <signing@zolt.test>", "default", "sign", "0"));
        assumeTrue(exitCode == 0, "gpg could not generate a throwaway signing key");
    }

    private int verify(Path gnupgHome, Path signature, Path artifact) throws IOException, InterruptedException {
        return runGpg(gnupgHome, List.of("--verify", signature.toString(), artifact.toString()));
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

    private static boolean gpgAvailable() {
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
}
