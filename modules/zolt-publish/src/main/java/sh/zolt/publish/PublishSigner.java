package sh.zolt.publish;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Produces detached, ASCII-armored GPG signatures ({@code .asc}) by shelling out to the {@code gpg}
 * binary — the Maven-ecosystem standard, chosen over embedding crypto in the native image. When a
 * passphrase is configured it is fed to gpg over stdin ({@code --passphrase-fd 0}), never as a
 * command-line argument, so it cannot leak through the process table. When no passphrase env is
 * configured, signing relies on {@code gpg-agent}.
 */
public final class PublishSigner {
    private final PublishSigningSettings settings;
    private final Function<String, String> environment;
    private final String gpgExecutable;

    public PublishSigner(PublishSigningSettings settings, Function<String, String> environment) {
        this(settings, environment, "gpg");
    }

    PublishSigner(PublishSigningSettings settings, Function<String, String> environment, String gpgExecutable) {
        this.settings = settings;
        this.environment = environment;
        this.gpgExecutable = gpgExecutable;
    }

    /** Signs {@code file}, writing {@code <file>.asc} beside it and returning that path. */
    public Path sign(Path file) {
        Path signature = file.resolveSibling(file.getFileName() + ".asc");
        try {
            Files.deleteIfExists(signature);
        } catch (IOException exception) {
            throw new PublishException("Could not remove stale signature at " + signature + ".", exception);
        }
        Optional<String> passphrase = passphrase();
        run(command(file, signature, passphrase.isPresent()), passphrase, file);
        if (!Files.isRegularFile(signature)) {
            throw new PublishException(
                    "gpg did not produce a signature for " + file
                            + ". Next: verify the signing key and gpg configuration.");
        }
        return signature;
    }

    private List<String> command(Path file, Path signature, boolean loopbackPassphrase) {
        List<String> command = new ArrayList<>();
        command.add(gpgExecutable);
        command.add("--batch");
        command.add("--yes");
        command.add("--armor");
        settings.keyId().ifPresent(keyId -> {
            command.add("--local-user");
            command.add(keyId);
        });
        if (loopbackPassphrase) {
            command.add("--pinentry-mode");
            command.add("loopback");
            command.add("--passphrase-fd");
            command.add("0");
        }
        command.add("--output");
        command.add(signature.toString());
        command.add("--detach-sign");
        command.add(file.toString());
        return command;
    }

    private Optional<String> passphrase() {
        if (settings.passphraseEnv().isEmpty()) {
            return Optional.empty();
        }
        String name = settings.passphraseEnv().orElseThrow();
        String value = environment.apply(name);
        if (value == null || value.isBlank()) {
            throw new PublishException(
                    "Signing passphrase environment variable " + name + " is not set. Next: export " + name
                            + " or remove [publish.signing].passphraseEnv to sign through gpg-agent.");
        }
        return Optional.of(value);
    }

    private void run(List<String> command, Optional<String> passphrase, Path file) {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        // Honor an explicit GNUPGHOME so signing keys resolve to the configured keyring; when unset,
        // gpg falls back to its default home inherited from the parent process.
        String gnupgHome = environment.apply("GNUPGHOME");
        if (gnupgHome != null && !gnupgHome.isBlank()) {
            builder.environment().put("GNUPGHOME", gnupgHome);
        }
        Process process;
        try {
            process = builder.start();
        } catch (IOException exception) {
            throw new PublishException(
                    "Could not run gpg to sign " + file
                            + ". Next: install GnuPG (gpg) or disable [publish.signing].",
                    exception);
        }
        try (OutputStream stdin = process.getOutputStream()) {
            if (passphrase.isPresent()) {
                stdin.write(passphrase.orElseThrow().getBytes(StandardCharsets.UTF_8));
                stdin.write('\n');
                stdin.flush();
            }
        } catch (IOException ignored) {
            // gpg may already have exited; the exit code below carries the real failure.
        }
        String output;
        int exitCode;
        try {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            exitCode = process.waitFor();
        } catch (IOException exception) {
            throw new PublishException("Could not read gpg output while signing " + file + ".", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PublishException("gpg signing was interrupted. Try publishing again.", exception);
        }
        if (exitCode != 0) {
            throw new PublishException(
                    "gpg failed to sign " + file + " (exit code " + exitCode
                            + "). Next: check the signing key, passphrase, and gpg-agent.\n"
                            + output.stripTrailing());
        }
    }
}
