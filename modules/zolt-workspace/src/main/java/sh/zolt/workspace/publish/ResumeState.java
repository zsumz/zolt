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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Durable v2 transaction manifest for an interrupted plain-repository publish, written under the
 * workspace target directory on failure and consulted when a {@code --resume-members} run resumes it.
 * Beyond the resumed members' upload-plan hash and semantic options, it binds the resume to concrete
 * facts so nothing is trusted blindly: for every resumable member its repository {@code targetIdentity}
 * and {@code signingIdentity}, for every staged file the SHA-256 of the exact staged bytes, and the
 * full set of repository paths that already landed. A resume therefore refuses a changed plan, a
 * changed repository destination, or a changed signing setup; verifies each already-landed path
 * against its recorded bytes rather than skipping it; and reuses staged signatures instead of
 * re-signing. The schema is bumped to {@code zolt.publish-resume.v2}; an older v1 file is refused
 * ("re-run the full publish") rather than guessed at.
 */
record ResumeState(
        String planHash,
        boolean allowMixedVersions,
        boolean sbom,
        List<String> members,
        Map<String, MemberContext> memberContexts,
        Map<String, String> stagedHashes,
        Set<String> completed) {
    private static final String SCHEMA = "zolt.publish-resume.v2";
    private static final String LEGACY_SCHEMA = "zolt.publish-resume.v1";

    ResumeState {
        members = List.copyOf(members);
        memberContexts = Map.copyOf(memberContexts);
        stagedHashes = Map.copyOf(stagedHashes);
        completed = Set.copyOf(completed);
    }

    /** The repository destination and signing setup a member was published under, for change detection. */
    record MemberContext(String targetIdentity, String signingIdentity) {
    }

    /** The result of reading a manifest: an absent file, an untrusted legacy v1 file, or a v2 manifest. */
    record ReadOutcome(boolean legacy, Optional<ResumeState> state) {
        boolean present() {
            return state.isPresent();
        }

        static ReadOutcome absent() {
            return new ReadOutcome(false, Optional.empty());
        }

        static ReadOutcome legacyV1() {
            return new ReadOutcome(true, Optional.empty());
        }

        static ReadOutcome of(ResumeState state) {
            return new ReadOutcome(false, Optional.of(state));
        }
    }

    /**
     * Builds the manifest to persist on a Phase-2 failure: the resumable members ({@code resumeSet},
     * the failed member and those after it) with their target + signing identities and every staged
     * file's exact-bytes SHA-256, plus {@code completed} — the full set of repository paths that have
     * landed, providers included, so an absent already-published sibling is provably satisfied on resume.
     */
    static ResumeState of(
            List<StagedMember> resumeSet,
            WorkspacePublishService.Options options,
            List<String> selection,
            Set<String> completed) {
        Map<String, MemberContext> contexts = new LinkedHashMap<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        for (StagedMember member : resumeSet) {
            contexts.put(member.memberPath(), new MemberContext(member.targetIdentity(), member.signingIdentity()));
            for (StagedArtifact artifact : member.artifacts()) {
                hashes.put(artifact.repositoryPath(), artifact.stagedSha256());
            }
        }
        return new ResumeState(
                planHash(resumeSet), options.allowMixedVersions(), options.sbom(), selection, contexts, hashes, completed);
    }

    /** Reads the manifest at {@code file}: absent when missing/unreadable, legacy when it is a v1 file. */
    static ReadOutcome read(Path file) {
        if (!Files.isRegularFile(file)) {
            return ReadOutcome.absent();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return ReadOutcome.absent();
        }
        String schema = value(lines, "schema");
        if (LEGACY_SCHEMA.equals(schema)) {
            return ReadOutcome.legacyV1();
        }
        if (!SCHEMA.equals(schema)) {
            return ReadOutcome.absent();
        }
        String planHash = "";
        boolean allowMixedVersions = false;
        boolean sbom = false;
        List<String> members = new ArrayList<>();
        Map<String, MemberContext> contexts = new LinkedHashMap<>();
        Map<String, String> hashes = new LinkedHashMap<>();
        Set<String> completed = new LinkedHashSet<>();
        for (String line : lines) {
            int equals = line.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String key = line.substring(0, equals);
            String value = line.substring(equals + 1);
            switch (key) {
                case "planHash" -> planHash = value;
                case "allowMixedVersions" -> allowMixedVersions = Boolean.parseBoolean(value);
                case "sbom" -> sbom = Boolean.parseBoolean(value);
                case "members" -> {
                    if (!value.isBlank()) {
                        members.addAll(List.of(value.split(",")));
                    }
                }
                case "member" -> {
                    String[] parts = value.split("\\|", 3);
                    if (parts.length == 3) {
                        contexts.put(parts[0], new MemberContext(parts[1], parts[2]));
                    }
                }
                case "file" -> {
                    String[] parts = value.split("\\|", 2);
                    if (parts.length == 2) {
                        hashes.put(parts[0], parts[1]);
                    }
                }
                case "completed" -> completed.add(value);
                default -> {
                    // Unknown key from a newer writer: ignore for forward tolerance.
                }
            }
        }
        if (planHash.isBlank()) {
            return ReadOutcome.absent();
        }
        return ReadOutcome.of(new ResumeState(planHash, allowMixedVersions, sbom, members, contexts, hashes, completed));
    }

    private static String value(List<String> lines, String key) {
        String prefix = key + "=";
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length());
            }
        }
        return "";
    }

    /** Writes this manifest to {@code file}, creating parent directories; entries sorted for determinism. */
    void write(Path file) {
        StringBuilder content = new StringBuilder();
        content.append("schema=").append(SCHEMA).append('\n');
        content.append("planHash=").append(planHash).append('\n');
        content.append("allowMixedVersions=").append(allowMixedVersions).append('\n');
        content.append("sbom=").append(sbom).append('\n');
        content.append("members=").append(String.join(",", members)).append('\n');
        for (Map.Entry<String, MemberContext> entry : new TreeMap<>(memberContexts).entrySet()) {
            content.append("member=").append(entry.getKey()).append('|')
                    .append(entry.getValue().targetIdentity()).append('|')
                    .append(entry.getValue().signingIdentity()).append('\n');
        }
        for (Map.Entry<String, String> entry : new TreeMap<>(stagedHashes).entrySet()) {
            content.append("file=").append(entry.getKey()).append('|').append(entry.getValue()).append('\n');
        }
        List<String> sortedCompleted = new ArrayList<>(completed);
        Collections.sort(sortedCompleted);
        for (String path : sortedCompleted) {
            content.append("completed=").append(path).append('\n');
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write publish resume state at " + file + ".", exception);
        }
    }

    /** The recorded exact-bytes SHA-256 for {@code repositoryPath}, if this manifest staged that file. */
    Optional<String> recordedHash(String repositoryPath) {
        return Optional.ofNullable(stagedHashes.get(repositoryPath));
    }

    /**
     * Whether an inter-member provider (a {@code group:artifact} coordinate) has already published: the
     * recorded completed paths contain at least one under its Maven directory. Used on resume to confirm
     * an absent sibling legitimately published rather than blindly assuming so.
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
     * Blockers when this manifest does not match the resume the operator asked for: a changed plan, a
     * mismatched member selection, mismatched semantic options, or — per member — a changed repository
     * target or signing configuration. Each says to re-run the full publish rather than resume blindly.
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
        for (StagedMember member : staged) {
            MemberContext recorded = memberContexts.get(member.memberPath());
            if (recorded == null) {
                continue; // a member absent from recorded contexts is already caught by the selection check
            }
            if (!recorded.targetIdentity().equals(member.targetIdentity())) {
                blockers.add("`" + member.coordinate() + "` now resolves to a different publish repository than the "
                        + "interrupted publish (recorded `" + recorded.targetIdentity() + "`, now `"
                        + member.targetIdentity() + "`); a resume must target the same repository. Re-run the full "
                        + "publish: `zolt publish --workspace`.");
            }
            if (!recorded.signingIdentity().equals(member.signingIdentity())) {
                blockers.add("the signing configuration for `" + member.coordinate() + "` changed since the "
                        + "interrupted publish (recorded `" + recorded.signingIdentity() + "`, now `"
                        + member.signingIdentity() + "`); a resume must sign identically. Re-run the full publish: "
                        + "`zolt publish --workspace`.");
            }
        }
        return blockers;
    }

    /**
     * An order-independent hash of a staged family's upload plan: every PRIMARY artifact's repository
     * path paired with the SHA-256 of its staged bytes. Checksums and detached signatures are excluded —
     * they derive from the primary bytes (so are redundant), and without {@code SOURCE_DATE_EPOCH} a
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
                entries.add(artifact.repositoryPath() + " " + artifact.stagedSha256());
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
}
