package sh.zolt.publish;

import java.io.IOException;
import java.io.OutputStream;
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
     * <p>Every body is first copied into immutable staging and then streamed from its staged file
     * straight into the zip at write time (see {@link EntrySource.FileSource}). Assembly memory is
     * therefore bounded independently of total publication size.
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
        PublicationStagingService staging = new PublicationStagingService(environment);
        staging.preflight(signing);
        List<PublicationSource> sources = new ArrayList<>();
        addPlan(sources, root, plan);
        Path bundlePath = root.resolve(plan.pomPath()).normalize().getParent().resolve("central-bundle.zip");
        List<BundleEntry> entries = stagedEntries(staging.stage(
                bundlePath.getParent().resolve("central-staging"), sources, signing));
        entries.sort(Comparator.comparing(BundleEntry::name));

        writeZip(bundlePath, entries);
        return new PublishCentralBundleResult(bundlePath, entries.stream().map(BundleEntry::name).toList());
    }

    /**
     * Assembles ONE deterministic bundle for an entire workspace family — every member's artifact,
     * POM, checksums, and (when signing) {@code .asc} — Central's atomic deployment unit. A family
     * publish is one bundle and one deployment id, never per-member deployments.
     */
    public PublishCentralBundleResult assembleFamily(Path bundleDirectory, List<Member> members) {
        PublicationStagingService staging = new PublicationStagingService(environment);
        staging.preflight(signing);
        List<PublicationSource> sources = new ArrayList<>();
        for (Member member : members) {
            addPlan(sources, member.memberRoot().toAbsolutePath().normalize(), member.plan());
        }
        Path bundlePath = bundleDirectory.toAbsolutePath().normalize().resolve("central-bundle.zip");
        List<BundleEntry> entries = stagedEntries(staging.stage(
                bundlePath.getParent().resolve("central-staging"), sources, signing));
        entries.sort(Comparator.comparing(BundleEntry::name));
        writeZip(bundlePath, entries);
        return new PublishCentralBundleResult(bundlePath, entries.stream().map(BundleEntry::name).toList());
    }

    private static void addPlan(List<PublicationSource> sources, Path root, PublishDryRunPlan plan) {
        if (!plan.pomOnly()) {
            sources.add(new PublicationSource(
                    plan.artifactUploadPath(), root.resolve(plan.artifactPath()).normalize()));
        }
        for (PublishArtifactPlan supplemental : plan.supplementalArtifacts()) {
            sources.add(new PublicationSource(
                    supplemental.uploadPath(), root.resolve(supplemental.path()).normalize()));
        }
        sources.add(new PublicationSource(
                plan.pomUploadPath(), root.resolve(plan.pomPath()).normalize()));
    }

    /** One family member's project root and its (possibly pom-only) plan. */
    public record Member(Path memberRoot, PublishDryRunPlan plan) {
    }

    private static List<BundleEntry> stagedEntries(List<StagedPublicationFile> staged) {
        return new ArrayList<>(staged.stream()
                .map(file -> BundleEntry.ofFile(file.repositoryPath(), file.source()))
                .toList());
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

        void writeContentTo(OutputStream out) throws IOException {
            source.writeTo(out);
        }
    }

    /** Where an entry's immutable staged bytes come from. */
    private sealed interface EntrySource {
        void writeTo(OutputStream out) throws IOException;

        record FileSource(Path file) implements EntrySource {
            @Override
            public void writeTo(OutputStream out) throws IOException {
                Files.copy(file, out);
            }
        }
    }
}
