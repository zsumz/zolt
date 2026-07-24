package sh.zolt.publish;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Freezes publication inputs before transfer. Every primary, supplemental, and POM is copied into a
 * dedicated staging tree; checksums and signatures are then derived only from those copies, and every
 * returned upload source is inside that tree.
 */
public final class PublicationStagingService {
    private final Function<String, String> environment;

    public PublicationStagingService() {
        this(System::getenv);
    }

    public PublicationStagingService(Function<String, String> environment) {
        this.environment = environment;
    }

    /** Exercises the exact signer configuration before any upload can begin. */
    public void preflight(PublishSigningSettings signing) {
        if (signing.enabled()) {
            new PublishSigner(signing, environment).preflight();
        }
    }

    public List<StagedPublicationFile> stage(
            Path stagingRoot,
            List<PublicationSource> sources,
            PublishSigningSettings signing) {
        return stage(stagingRoot, sources, signing, PublicationResume.none());
    }

    /**
     * Stages all sources and their derived files. Recorded resume bytes are reused from the staging
     * tree when intact; a missing non-reproducible signature is refused instead of silently replaced.
     */
    public List<StagedPublicationFile> stage(
            Path stagingRoot,
            List<PublicationSource> sources,
            PublishSigningSettings signing,
            PublicationResume resume) {
        Path root = stagingRoot.toAbsolutePath().normalize();
        Map<String, String> recorded = resume.recordedHashes();
        PublishSigner signer = signing.enabled() ? new PublishSigner(signing, environment) : null;
        boolean reproducible = signing.enabled() && SourceDateEpoch.parse(environment).reproducible();
        List<StagedPublicationFile> staged = new ArrayList<>();
        for (PublicationSource source : sources) {
            Path content = stagedPath(root, source.repositoryPath());
            freeze(source.source().toAbsolutePath().normalize(), content, source.repositoryPath(), recorded);
            addContent(staged, source.repositoryPath(), content, root);
            if (signer != null) {
                String signaturePath = source.repositoryPath() + ".asc";
                Path signature = stagedPath(root, signaturePath);
                stageSignature(content, signature, signaturePath, signer, reproducible, recorded);
                addContent(staged, signaturePath, signature, root);
            }
        }
        return List.copyOf(staged);
    }

    /** Stable signing identity used by durable resume manifests, including the exact frozen epoch. */
    public String signingIdentity(PublishSigningSettings signing) {
        if (!signing.enabled()) {
            return "unsigned";
        }
        Optional<Long> epoch = SourceDateEpoch.parse(environment).epochSeconds();
        return "key=" + signing.keyId().orElse("") + ";sde=" + epoch.map(String::valueOf).orElse("none");
    }

    private static void freeze(
            Path source,
            Path staged,
            String repositoryPath,
            Map<String, String> recorded) {
        String expected = recorded.get(repositoryPath);
        if (expected != null && Files.isRegularFile(staged) && sha256(staged).equals(expected)) {
            return;
        }
        try {
            Files.createDirectories(staged.getParent());
            if (!source.equals(staged)) {
                Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new PublishException(
                    "Could not freeze publication input `" + repositoryPath + "` from " + source + ".",
                    exception);
        }
        if (expected != null && !sha256(staged).equals(expected)) {
            throw new PublishException(
                    "Cannot resume because the exact staged bytes for `"
                            + repositoryPath
                            + "` are no longer available. Re-run the full publish.");
        }
    }

    private static void stageSignature(
            Path content,
            Path signature,
            String signaturePath,
            PublishSigner signer,
            boolean reproducible,
            Map<String, String> recorded) {
        String expected = recorded.get(signaturePath);
        if (expected != null && Files.isRegularFile(signature) && sha256(signature).equals(expected)) {
            return;
        }
        if (expected != null && !reproducible) {
            throw new PublishException(
                    "Cannot resume because the staged signature for `"
                            + signaturePath
                            + "` was lost or changed and wall-clock signing cannot reproduce its exact bytes.");
        }
        Path generated = signer.sign(content);
        if (!generated.equals(signature)) {
            try {
                Files.createDirectories(signature.getParent());
                Files.move(generated, signature, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                throw new PublishException("Could not stage detached signature `" + signaturePath + "`.", exception);
            }
        }
        if (expected != null && !sha256(signature).equals(expected)) {
            throw new PublishException(
                    "Recreated signature `" + signaturePath + "` does not match the interrupted publish.");
        }
    }

    private static void addContent(
            List<StagedPublicationFile> staged,
            String repositoryPath,
            Path content,
            Path root) {
        List<PublishChecksum.Sidecar> sidecars = PublishChecksum.sidecars(content);
        staged.add(new StagedPublicationFile(repositoryPath, content, sidecar(sidecars, "sha256")));
        for (PublishChecksum.Sidecar sidecar : sidecars) {
            String sidecarPath = repositoryPath + "." + sidecar.extension();
            Path file = stagedPath(root, sidecarPath);
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, sidecar.value());
            } catch (IOException exception) {
                throw new PublishException("Could not stage checksum sidecar `" + sidecarPath + "`.", exception);
            }
            staged.add(new StagedPublicationFile(sidecarPath, file, sha256(file)));
        }
    }

    private static Path stagedPath(Path root, String repositoryPath) {
        Path staged = root.resolve(repositoryPath).normalize();
        if (!staged.startsWith(root) || Path.of(repositoryPath).isAbsolute()) {
            throw new PublishException("Invalid publication repository path `" + repositoryPath + "`.");
        }
        return staged;
    }

    private static String sidecar(List<PublishChecksum.Sidecar> sidecars, String extension) {
        return sidecars.stream()
                .filter(sidecar -> sidecar.extension().equals(extension))
                .map(PublishChecksum.Sidecar::value)
                .findFirst()
                .orElseThrow();
    }

    private static String sha256(Path path) {
        return PublishChecksum.hex(path, "SHA-256");
    }
}
