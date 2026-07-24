package sh.zolt.publish;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PublishCentralBundleTest {
    private static final String PASSPHRASE = "zolt-bundle-passphrase";

    @TempDir
    private Path tempDir;

    @Test
    void assemblesMavenLayoutWithChecksumsForEveryFileAndIsDeterministic() throws IOException {
        Path root = writeProject();
        PublishDryRunPlan plan = plan();
        PublishCentralBundle bundle = new PublishCentralBundle(PublishSigningSettings.disabled(), name -> null);

        PublishCentralBundleResult result = bundle.assemble(root, plan);

        List<String> entries = result.entries();
        String jar = "com/example/app/1.0.0/app-1.0.0.jar";
        String sources = "com/example/app/1.0.0/app-1.0.0-sources.jar";
        String pom = "com/example/app/1.0.0/app-1.0.0.pom";
        for (String base : List.of(jar, sources, pom)) {
            assertTrue(entries.contains(base), () -> "missing " + base + " in " + entries);
            assertTrue(entries.contains(base + ".md5"), () -> "missing " + base + ".md5");
            assertTrue(entries.contains(base + ".sha1"), () -> "missing " + base + ".sha1");
            assertTrue(entries.contains(base + ".sha256"), () -> "missing " + base + ".sha256");
        }
        // No signatures when signing is disabled.
        assertFalse(entries.stream().anyMatch(entry -> entry.endsWith(".asc")), entries.toString());
        // Entries are sorted for a reproducible bundle.
        List<String> sorted = new ArrayList<>(entries);
        sorted.sort(String::compareTo);
        assertEquals(sorted, entries);

        byte[] first = Files.readAllBytes(result.bundlePath());
        byte[] second = Files.readAllBytes(new PublishCentralBundle(PublishSigningSettings.disabled(), name -> null)
                .assemble(root, plan).bundlePath());
        assertArrayEquals(first, second, "bundle should be byte-for-byte reproducible");
    }

    @Test
    void signingAddsDetachedSignaturesAndTheirChecksums() throws Exception {
        assumeTrue(gpgAvailable(), "gpg is not installed");
        Path gnupgHome = isolatedGnupgHome();
        assumeTrue(generateSigningKey(gnupgHome), "gpg could not generate a throwaway signing key");

        Path root = writeProject();
        PublishDryRunPlan plan = plan();
        Function<String, String> environment = Map.of(
                "ZOLT_BUNDLE_PASS", PASSPHRASE,
                "GNUPGHOME", gnupgHome.toString())::get;
        PublishCentralBundle bundle = new PublishCentralBundle(
                new PublishSigningSettings(true, Optional.empty(), Optional.of("ZOLT_BUNDLE_PASS")),
                environment);

        PublishCentralBundleResult result = bundle.assemble(root, plan);

        String jar = "com/example/app/1.0.0/app-1.0.0.jar";
        assertTrue(result.entries().contains(jar + ".asc"), result.entries().toString());
        assertTrue(result.entries().contains(jar + ".asc.sha1"), result.entries().toString());
        assertTrue(result.entries().contains("com/example/app/1.0.0/app-1.0.0.pom.asc"), result.entries().toString());
        try (ZipFile zip = new ZipFile(result.bundlePath().toFile())) {
            byte[] signature = zip.getInputStream(zip.getEntry(jar + ".asc")).readAllBytes();
            assertTrue(new String(signature, StandardCharsets.UTF_8).contains("-----BEGIN PGP SIGNATURE-----"));
        }
    }

    @Test
    void signedBundleIsByteIdenticalUnderSourceDateEpoch() throws Exception {
        assumeTrue(gpgAvailable(), "gpg is not installed");
        Path gnupgHome = isolatedGnupgHome();
        assumeTrue(generateSigningKey(gnupgHome), "gpg could not generate a throwaway signing key");
        String keyId = signingKeyId(gnupgHome);
        long epoch = Instant.now().getEpochSecond();

        Path root = writeProject();
        PublishDryRunPlan plan = plan();
        Function<String, String> environment = Map.of(
                "ZOLT_BUNDLE_PASS", PASSPHRASE,
                "GNUPGHOME", gnupgHome.toString(),
                "SOURCE_DATE_EPOCH", Long.toString(epoch))::get;
        PublishSigningSettings signing =
                new PublishSigningSettings(true, Optional.of(keyId), Optional.of("ZOLT_BUNDLE_PASS"));

        PublishCentralBundleResult firstResult = new PublishCentralBundle(signing, environment).assemble(root, plan);
        byte[] first = Files.readAllBytes(firstResult.bundlePath());
        assertTrue(
                firstResult.entries().stream().anyMatch(entry -> entry.endsWith(".asc")),
                "the signed bundle should contain .asc signatures");
        Thread.sleep(1100); // advance the wall clock so a non-frozen signing time WOULD differ
        byte[] second = Files.readAllBytes(
                new PublishCentralBundle(signing, environment).assemble(root, plan).bundlePath());

        assertArrayEquals(
                first,
                second,
                "a signed Central bundle must be byte-for-byte reproducible under SOURCE_DATE_EPOCH");
    }

    private Path writeProject() throws IOException {
        Path root = tempDir.resolve("project");
        Files.createDirectories(root.resolve("target/publish"));
        Files.writeString(root.resolve("target/app-1.0.0.jar"), "artifact\n");
        Files.writeString(root.resolve("target/app-1.0.0-sources.jar"), "sources\n");
        Files.writeString(root.resolve("target/publish/app-1.0.0.pom"), "<project/>\n");
        return root;
    }

    private static PublishDryRunPlan plan() {
        PublishArtifactPlan sources = new PublishArtifactPlan(
                "sources",
                Optional.of("sources"),
                Path.of("target/app-1.0.0-sources.jar"),
                "sha256:sources",
                "com/example/app/1.0.0/app-1.0.0-sources.jar");
        return new PublishDryRunPlan(
                "com.example:app:1.0.0",
                "release",
                "central",
                "https://central.sonatype.com",
                "main",
                Path.of("target/app-1.0.0.jar"),
                "sha256:main",
                "com/example/app/1.0.0/app-1.0.0.jar",
                List.of(sources),
                Path.of("target/app-1.0.0.jar.zolt-package.json"),
                Path.of("target/publish/app-1.0.0.pom"),
                "sha256:pom",
                "com/example/app/1.0.0/app-1.0.0.pom",
                List.of(),
                "",
                List.of(),
                false);
    }

    private Path isolatedGnupgHome() throws IOException {
        Path gnupgHome = tempDir.resolve("gnupg");
        Files.createDirectories(gnupgHome);
        try {
            Files.setPosixFilePermissions(gnupgHome, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystem.
        }
        return gnupgHome;
    }

    private boolean generateSigningKey(Path gnupgHome) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("gpg"));
        command.addAll(List.of(
                "--batch", "--pinentry-mode", "loopback", "--passphrase", PASSPHRASE,
                "--quick-generate-key", "Zolt Bundle Test <bundle@zolt.test>", "default", "sign", "0"));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("GNUPGHOME", gnupgHome.toString());
        Process process = builder.start();
        process.getInputStream().readAllBytes();
        return process.waitFor() == 0;
    }

    /** Reads the throwaway key's fingerprint so the test can pin {@code keyId} for reproducible signing. */
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
