package sh.zolt.workspace.publish;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Durable resume state for an interrupted plain-repository publish, written to a deterministic path
 * under the workspace target directory on failure and consulted when a {@code --resume-members} run
 * resumes it. It records the repository paths that already landed, a hash of the resumed members'
 * upload plan, and the original semantic options — so a resume is backed by real state, not a trusted
 * hidden flag: a manual resume without matching state, or against a plan that has since changed,
 * refuses actionably instead of silently treating absent providers as published.
 */
record ResumeState(
        String planHash, boolean allowMixedVersions, boolean sbom, List<String> members, Set<String> completed) {
    private static final String SCHEMA = "zolt.publish-resume.v1";

    ResumeState {
        members = List.copyOf(members);
        completed = Set.copyOf(completed);
    }

    /** Reads the state at {@code file}, or empty when it is absent or not the expected schema. */
    static Optional<ResumeState> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String planHash = "";
            boolean allowMixedVersions = false;
            boolean sbom = false;
            List<String> members = new ArrayList<>();
            Set<String> completed = new LinkedHashSet<>();
            boolean schemaMatched = false;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int equals = line.indexOf('=');
                if (equals < 0) {
                    continue;
                }
                String key = line.substring(0, equals);
                String value = line.substring(equals + 1);
                switch (key) {
                    case "schema" -> schemaMatched = SCHEMA.equals(value);
                    case "planHash" -> planHash = value;
                    case "allowMixedVersions" -> allowMixedVersions = Boolean.parseBoolean(value);
                    case "sbom" -> sbom = Boolean.parseBoolean(value);
                    case "members" -> {
                        if (!value.isBlank()) {
                            members.addAll(List.of(value.split(",")));
                        }
                    }
                    case "completed" -> completed.add(value);
                    default -> {
                        // Unknown key from a newer writer: ignore for forward tolerance.
                    }
                }
            }
            if (!schemaMatched || planHash.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new ResumeState(planHash, allowMixedVersions, sbom, members, completed));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    /** Writes this state to {@code file}, creating parent directories. */
    void write(Path file) {
        StringBuilder content = new StringBuilder();
        content.append("schema=").append(SCHEMA).append('\n');
        content.append("planHash=").append(planHash).append('\n');
        content.append("allowMixedVersions=").append(allowMixedVersions).append('\n');
        content.append("sbom=").append(sbom).append('\n');
        content.append("members=").append(String.join(",", members)).append('\n');
        List<String> sorted = new ArrayList<>(completed);
        Collections.sort(sorted);
        for (String path : sorted) {
            content.append("completed=").append(path).append('\n');
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write publish resume state at " + file + ".", exception);
        }
    }

    /**
     * Whether an inter-member provider (a {@code group:artifact} coordinate) has already published:
     * the recorded completed paths contain at least one under its Maven directory. Used on resume to
     * confirm an absent sibling legitimately published rather than blindly assuming so.
     */
    boolean recordsPublished(String groupArtifact) {
        String[] parts = groupArtifact.split(":", 2);
        if (parts.length != 2) {
            return false;
        }
        String prefix = parts[0].replace('.', '/') + "/" + parts[1] + "/";
        return completed.stream().anyMatch(path -> path.startsWith(prefix));
    }

    /**
     * Blockers when this state does not match the resume the operator asked for: a changed plan (the
     * artifacts or versions differ from the interrupted publish), a mismatched member selection, or
     * mismatched semantic options. Each says to re-run the full publish rather than resume blindly.
     */
    List<String> validate(
            List<StagedMember> staged, WorkspacePublishService.Options options, List<String> selection) {
        List<String> blockers = new ArrayList<>();
        if (!planHash.equals(planHash(staged))) {
            blockers.add("the publish plan changed since the interrupted publish (artifacts or versions differ); a "
                    + "stale resume would upload inconsistent bytes. Re-run the full publish: `zolt publish --workspace`.");
            return blockers;
        }
        if (!new LinkedHashSet<>(members).equals(new LinkedHashSet<>(selection))) {
            blockers.add("the --resume-members selection does not match the interrupted publish (recorded "
                    + String.join(",", members) + "). Re-run the full publish: `zolt publish --workspace`.");
        }
        if (allowMixedVersions != options.allowMixedVersions() || sbom != options.sbom()) {
            blockers.add("the resume options do not match the interrupted publish; run the emitted resume command "
                    + "unchanged, or re-run the full publish: `zolt publish --workspace`.");
        }
        return blockers;
    }

    /**
     * An order-independent hash of a staged family's upload plan: every PRIMARY artifact's repository
     * path paired with the SHA-256 of its bytes. Checksums and detached signatures are excluded — they
     * derive from the primary bytes (so are redundant), and without {@code SOURCE_DATE_EPOCH} a
     * wall-clock signature is non-deterministic and would spuriously fail a legitimate resume. Identical
     * across the interrupted publish and its resume when the plan is unchanged; any changed artifact,
     * version, or path shifts it.
     */
    static String planHash(List<StagedMember> members) {
        List<String> entries = new ArrayList<>();
        for (StagedMember member : members) {
            for (StagedArtifact artifact : member.artifacts()) {
                if (isDerived(artifact.repositoryPath())) {
                    continue;
                }
                entries.add(artifact.repositoryPath() + " " + fileSha256(artifact.source()));
            }
        }
        Collections.sort(entries);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", entries).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean isDerived(String repositoryPath) {
        return repositoryPath.endsWith(".md5")
                || repositoryPath.endsWith(".sha1")
                || repositoryPath.endsWith(".sha256")
                || repositoryPath.endsWith(".asc");
    }

    private static String fileSha256(Path source) {
        try {
            return Sha256.hex(source);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not hash staged artifact at " + source + ".", exception);
        }
    }
}
