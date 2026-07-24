package sh.zolt.publish;

import java.io.IOException;
import java.io.OutputStream;
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
 * checksums of the signature). Entries are sorted and written with a fixed timestamp, so the bundle
 * is byte-for-byte reproducible given reproducible inputs. Unsigned bundles are always reproducible;
 * signatures embed a creation time, so signed bundles are reproducible only when {@code
 * SOURCE_DATE_EPOCH} freezes that time and {@code keyId} pins the key (see {@link PublishSigner}).
 *
 * <p>Artifact, POM, and signature bodies are streamed from their files straight into the zip at
 * write time (see {@link EntrySource.FileSource}); only small generated checksum bodies are held in
 * memory. Assembly memory is therefore bounded by the largest checksum sidecar rather than by the
 * total publication size, so a large family does not read every artifact into heap at once.
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
        addPlan(entries, root, plan, signer);
        entries.sort(Comparator.comparing(BundleEntry::name));

        Path bundlePath = root.resolve(plan.pomPath()).normalize().getParent().resolve("central-bundle.zip");
        writeZip(bundlePath, entries);
        return new PublishCentralBundleResult(bundlePath, entries.stream().map(BundleEntry::name).toList());
    }

    /**
     * Assembles ONE deterministic bundle for an entire workspace family — every member's artifact,
     * POM, checksums, and (when signing) {@code .asc} — Central's atomic deployment unit. A family
     * publish is one bundle and one deployment id, never per-member deployments.
     */
    public PublishCentralBundleResult assembleFamily(Path bundleDirectory, List<Member> members) {
        PublishSigner signer = signing.enabled() ? new PublishSigner(signing, environment) : null;
        List<BundleEntry> entries = new ArrayList<>();
        for (Member member : members) {
            addPlan(entries, member.memberRoot().toAbsolutePath().normalize(), member.plan(), signer);
        }
        entries.sort(Comparator.comparing(BundleEntry::name));
        Path bundlePath = bundleDirectory.toAbsolutePath().normalize().resolve("central-bundle.zip");
        writeZip(bundlePath, entries);
        return new PublishCentralBundleResult(bundlePath, entries.stream().map(BundleEntry::name).toList());
    }

    private void addPlan(List<BundleEntry> entries, Path root, PublishDryRunPlan plan, PublishSigner signer) {
        if (!plan.pomOnly()) {
            addFile(entries, root.resolve(plan.artifactPath()).normalize(), plan.artifactUploadPath(), signer);
        }
        for (PublishArtifactPlan supplemental : plan.supplementalArtifacts()) {
            addFile(entries, root.resolve(supplemental.path()).normalize(), supplemental.uploadPath(), signer);
        }
        addFile(entries, root.resolve(plan.pomPath()).normalize(), plan.pomUploadPath(), signer);
    }

    /** One family member's project root and its (possibly pom-only) plan. */
    public record Member(Path memberRoot, PublishDryRunPlan plan) {
    }

    private void addFile(List<BundleEntry> entries, Path localFile, String uploadPath, PublishSigner signer) {
        entries.add(BundleEntry.ofFile(uploadPath, localFile));
        addChecksums(entries, localFile, uploadPath);
        if (signer != null) {
            Path signature = signer.sign(localFile);
            String signaturePath = uploadPath + ".asc";
            entries.add(BundleEntry.ofFile(signaturePath, signature));
            addChecksums(entries, signature, signaturePath);
        }
    }

    private static void addChecksums(List<BundleEntry> entries, Path file, String uploadPath) {
        for (PublishChecksum.Sidecar sidecar : PublishChecksum.sidecars(file)) {
            entries.add(BundleEntry.ofInline(
                    uploadPath + "." + sidecar.extension(),
                    sidecar.value().getBytes(StandardCharsets.UTF_8)));
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
                    entry.writeContentTo(zip);
                    zip.closeEntry();
                }
            }
        } catch (IOException exception) {
            throw new PublishException("Could not write Central bundle at " + bundlePath + ".", exception);
        }
    }

    /**
     * A single zip entry: its Maven-layout name plus the source of its bytes. Entries are sorted by
     * {@link #name()} for determinism before any content is read, then each source is streamed into
     * the zip in {@link #writeContentTo(OutputStream)}.
     */
    private record BundleEntry(String name, EntrySource source) {
        static BundleEntry ofFile(String name, Path file) {
            return new BundleEntry(name, new EntrySource.FileSource(file));
        }

        static BundleEntry ofInline(String name, byte[] bytes) {
            return new BundleEntry(name, new EntrySource.InlineSource(bytes));
        }

        void writeContentTo(OutputStream out) throws IOException {
            source.writeTo(out);
        }
    }

    /**
     * Where an entry's bytes come from. A {@link FileSource} is streamed from disk so the artifact is
     * never fully resident in heap; an {@link InlineSource} carries a small, already-generated body
     * (a checksum sidecar) that has nowhere on disk to stream from.
     */
    private sealed interface EntrySource {
        void writeTo(OutputStream out) throws IOException;

        record FileSource(Path file) implements EntrySource {
            @Override
            public void writeTo(OutputStream out) throws IOException {
                Files.copy(file, out);
            }
        }

        record InlineSource(byte[] bytes) implements EntrySource {
            @Override
            public void writeTo(OutputStream out) throws IOException {
                out.write(bytes);
            }
        }
    }
}
