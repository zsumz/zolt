package sh.zolt.publish;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Assembles the Sonatype Central Portal upload bundle: a deterministic zip in Maven repository
 * layout containing every artifact and the POM, each accompanied by its {@code .md5}/{@code .sha1}/
 * {@code .sha256} checksums and, when signing is enabled, a {@code .asc} detached signature (with
 * checksums of the signature). Entries are sorted and written with a fixed timestamp so the bundle
 * is byte-for-byte reproducible.
 */
public final class PublishCentralBundle {
    private static final long DETERMINISTIC_ENTRY_TIME = 0L;

    private final PublishSigningSettings signing;
    private final Function<String, String> environment;

    public PublishCentralBundle(PublishSigningSettings signing, Function<String, String> environment) {
        this.signing = signing;
        this.environment = environment;
    }

    public PublishCentralBundleResult assemble(Path projectRoot, PublishDryRunPlan plan) {
        Path root = projectRoot.toAbsolutePath().normalize();
        PublishSigner signer = signing.enabled() ? new PublishSigner(signing, environment) : null;
        List<BundleEntry> entries = new ArrayList<>();
        addFile(entries, root.resolve(plan.artifactPath()).normalize(), plan.artifactUploadPath(), signer);
        for (PublishArtifactPlan supplemental : plan.supplementalArtifacts()) {
            addFile(entries, root.resolve(supplemental.path()).normalize(), supplemental.uploadPath(), signer);
        }
        addFile(entries, root.resolve(plan.pomPath()).normalize(), plan.pomUploadPath(), signer);
        entries.sort(Comparator.comparing(BundleEntry::name));

        Path bundlePath = root.resolve(plan.pomPath()).normalize().getParent().resolve("central-bundle.zip");
        writeZip(bundlePath, entries);
        return new PublishCentralBundleResult(bundlePath, entries.stream().map(BundleEntry::name).toList());
    }

    private void addFile(List<BundleEntry> entries, Path localFile, String uploadPath, PublishSigner signer) {
        entries.add(new BundleEntry(uploadPath, read(localFile)));
        addChecksums(entries, localFile, uploadPath);
        if (signer != null) {
            Path signature = signer.sign(localFile);
            String signaturePath = uploadPath + ".asc";
            entries.add(new BundleEntry(signaturePath, read(signature)));
            addChecksums(entries, signature, signaturePath);
        }
    }

    private static void addChecksums(List<BundleEntry> entries, Path file, String uploadPath) {
        for (PublishChecksum.Sidecar sidecar : PublishChecksum.sidecars(file)) {
            entries.add(new BundleEntry(
                    uploadPath + "." + sidecar.extension(),
                    sidecar.value().getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new PublishException("Could not read bundle input " + file + ".", exception);
        }
    }

    private static void writeZip(Path bundlePath, List<BundleEntry> entries) {
        try {
            Files.createDirectories(bundlePath.getParent());
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(bundlePath))) {
                for (BundleEntry entry : entries) {
                    ZipEntry zipEntry = new ZipEntry(entry.name());
                    zipEntry.setTime(DETERMINISTIC_ENTRY_TIME);
                    zip.putNextEntry(zipEntry);
                    zip.write(entry.content());
                    zip.closeEntry();
                }
            }
        } catch (IOException exception) {
            throw new PublishException("Could not write Central bundle at " + bundlePath + ".", exception);
        }
    }

    private record BundleEntry(String name, byte[] content) {
    }
}
