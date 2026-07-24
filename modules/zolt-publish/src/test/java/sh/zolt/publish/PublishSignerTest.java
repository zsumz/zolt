package sh.zolt.publish;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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

    @Test
    void freezesSignatureTimeUnderSourceDateEpochForByteIdenticalSignatures() throws Exception {
        assumeTrue(gpgAvailable(), "gpg is not installed");
        Path gnupgHome = isolatedGnupgHome();
        generateSigningKey(gnupgHome);
        String keyId = signingKeyId(gnupgHome);
        // Capture the epoch after key generation so it is on/after the key's creation second — the
        // real-world case where the release commit's SOURCE_DATE_EPOCH follows key creation.
        long epoch = Instant.now().getEpochSecond();
        Path artifact = Files.writeString(tempDir.resolve("artifact.jar"), "zolt reproducible payload\n");

        PublishSigner signer = new PublishSigner(
                new PublishSigningSettings(true, Optional.of(keyId), Optional.of("ZOLT_TEST_GPG_PASS")),
                environment(gnupgHome, epoch));

        Path signature = signer.sign(artifact);
        byte[] first = Files.readAllBytes(signature);
        Thread.sleep(1100); // cross a wall-clock second so a non-frozen signing time WOULD differ
        signer.sign(artifact);
        byte[] second = Files.readAllBytes(signature);

        assertArrayEquals(
                first,
                second,
                "SOURCE_DATE_EPOCH must freeze the signature creation time for byte-identical .asc output");
        assertEquals(0, verify(gnupgHome, signature, artifact), "the deterministic signature must still verify");
    }

    @Test
    void signaturesDifferWithoutFakeTimeConfirmingTheTimestampIsTheVariable() throws Exception {
        assumeTrue(gpgAvailable(), "gpg is not installed");
        Path gnupgHome = isolatedGnupgHome();
        generateSigningKey(gnupgHome);
        String keyId = signingKeyId(gnupgHome);
        Path artifact = Files.writeString(tempDir.resolve("artifact.jar"), "zolt reproducible payload\n");

        // Same pinned key, no SOURCE_DATE_EPOCH: only the embedded wall-clock time varies between runs.
        PublishSigner signer = new PublishSigner(
                new PublishSigningSettings(true, Optional.of(keyId), Optional.of("ZOLT_TEST_GPG_PASS")),
                environment(gnupgHome));

        Path signature = signer.sign(artifact);
        byte[] first = Files.readAllBytes(signature);
        Thread.sleep(1100);
        signer.sign(artifact);
        byte[] second = Files.readAllBytes(signature);

        assertFalse(
                Arrays.equals(first, second),
                "without SOURCE_DATE_EPOCH the wall-clock signature time makes repeated runs differ");
    }

    @Test
    void reproducibleSigningWithoutPinnedKeyRaisesActionableError() throws IOException {
        Path artifact = Files.writeString(tempDir.resolve("artifact.jar"), "payload\n");
        // Signing enabled + SOURCE_DATE_EPOCH set + no keyId: the ambiguous-key determinism hazard.
        Map<String, String> environment = Map.of(
                "ZOLT_TEST_GPG_PASS", PASSPHRASE,
                "SOURCE_DATE_EPOCH", "1700000000");
        PublishSigner signer = new PublishSigner(
                new PublishSigningSettings(true, Optional.empty(), Optional.of("ZOLT_TEST_GPG_PASS")),
                environment::get);

        PublishException exception = assertThrows(PublishException.class, () -> signer.sign(artifact));

        assertTrue(exception.getMessage().contains("keyId"));
        assertTrue(exception.getMessage().contains("SOURCE_DATE_EPOCH"));
        assertTrue(exception.getMessage().contains("Next:"));
    }

    private Function<String, String> environment(Path gnupgHome) {
        Map<String, String> values = Map.of(
                "ZOLT_TEST_GPG_PASS", PASSPHRASE,
                "GNUPGHOME", gnupgHome.toString());
        return values::get;
    }

    private Function<String, String> environment(Path gnupgHome, long sourceDateEpoch) {
        Map<String, String> values = Map.of(
                "ZOLT_TEST_GPG_PASS", PASSPHRASE,
                "GNUPGHOME", gnupgHome.toString(),
                "SOURCE_DATE_EPOCH", Long.toString(sourceDateEpoch));
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

    /** Reads the throwaway key's fingerprint so tests can pin {@code keyId} for reproducible signing. */
    private String signingKeyId(Path gnupgHome) throws IOException, InterruptedException {
        ProcessBuilder builder =
                new ProcessBuilder("gpg", "--list-secret-keys", "--with-colons").redirectErrorStream(true);
        builder.environment().put("GNUPGHOME", gnupgHome.toString());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        for (String line : output.split("\n")) {
            if (line.startsWith("fpr:")) {
                String[] fields = line.split(":");
                if (fields.length > 9 && !fields[9].isBlank()) {
                    return fields[9];
                }
            }
        }
        throw new IllegalStateException("Could not read a signing key fingerprint:\n" + output);
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
