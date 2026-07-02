package sh.zolt.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fixture-matrix coverage for the dependency-free {@code .git} reader. Each test builds a minimal temp
 * {@code .git} layout under a {@link TempDir} and asserts the resolved provenance.
 */
final class GitProvenanceReaderTest {

    private static final String SHA_MAIN = "1111111111111111111111111111111111111111";
    private static final String SHA_PACKED = "2222222222222222222222222222222222222222";
    private static final String SHA_DETACHED = "abcdef0123456789abcdef0123456789abcdef01";
    private static final String SHA_SHA256 =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final GitProvenanceReader reader = new GitProvenanceReader();

    @Test
    void normalBranchResolvesViaLooseRef(@TempDir Path root) throws IOException {
        Path gitDir = Files.createDirectory(root.resolve(".git"));
        writeHead(gitDir, "ref: refs/heads/main\n");
        writeLooseRef(gitDir, "refs/heads/main", SHA_MAIN);

        GitProvenance provenance = reader.read(root).orElseThrow();

        assertEquals(Optional.of(SHA_MAIN), provenance.commitSha());
        assertEquals(Optional.of("111111111111"), provenance.shortSha());
        assertEquals(Optional.of("main"), provenance.branch());
        assertFalse(provenance.detached());
        assertTrue(provenance.dirty().isEmpty());
    }

    @Test
    void packedRefsFallbackWhenNoLooseRef(@TempDir Path root) throws IOException {
        Path gitDir = Files.createDirectory(root.resolve(".git"));
        writeHead(gitDir, "ref: refs/heads/main\n");
        writePackedRefs(
                gitDir,
                "# pack-refs with: peeled fully-peeled sorted\n" + SHA_PACKED + " refs/heads/main\n");

        GitProvenance provenance = reader.read(root).orElseThrow();

        assertEquals(Optional.of(SHA_PACKED), provenance.commitSha());
        assertEquals(Optional.of("main"), provenance.branch());
        assertFalse(provenance.detached());
    }

    @Test
    void looseRefBeatsPackedRefs(@TempDir Path root) throws IOException {
        // This mirrors the Zolt repo itself: loose and packed disagree; loose must win.
        Path gitDir = Files.createDirectory(root.resolve(".git"));
        writeHead(gitDir, "ref: refs/heads/main\n");
        writeLooseRef(gitDir, "refs/heads/main", SHA_MAIN);
        writePackedRefs(gitDir, SHA_PACKED + " refs/heads/main\n");

        GitProvenance provenance = reader.read(root).orElseThrow();

        assertEquals(Optional.of(SHA_MAIN), provenance.commitSha());
    }

    @Test
    void packedRefsSkipsCommentAndPeelLines(@TempDir Path root) throws IOException {
        Path gitDir = Files.createDirectory(root.resolve(".git"));
        writeHead(gitDir, "ref: refs/heads/main\n");
        writePackedRefs(
                gitDir,
                "# pack-refs with: peeled fully-peeled sorted\n"
                        + "3333333333333333333333333333333333333333 refs/tags/v1\n"
                        + "^4444444444444444444444444444444444444444\n"
                        + SHA_PACKED
                        + " refs/heads/main\n");

        GitProvenance provenance = reader.read(root).orElseThrow();

        assertEquals(Optional.of(SHA_PACKED), provenance.commitSha());
    }

    @Test
    void detachedHeadIsRawSha(@TempDir Path root) throws IOException {
        Path gitDir = Files.createDirectory(root.resolve(".git"));
        writeHead(gitDir, SHA_DETACHED + "\n");

        GitProvenance provenance = reader.read(root).orElseThrow();

        assertEquals(Optional.of(SHA_DETACHED), provenance.commitSha());
        assertEquals(Optional.of("abcdef012345"), provenance.shortSha());
        assertTrue(provenance.branch().isEmpty());
        assertTrue(provenance.detached());
    }

    @Test
    void detachedHeadAcceptsSha256(@TempDir Path root) throws IOException {
        Path gitDir = Files.createDirectory(root.resolve(".git"));
        writeHead(gitDir, SHA_SHA256 + "\n");

        GitProvenance provenance = reader.read(root).orElseThrow();

        assertEquals(Optional.of(SHA_SHA256), provenance.commitSha());
        assertEquals(Optional.of("0123456789ab"), provenance.shortSha());
        assertTrue(provenance.detached());
    }

    @Test
    void chainedSymrefIsFollowed(@TempDir Path root) throws IOException {
        Path gitDir = Files.createDirectory(root.resolve(".git"));
        writeHead(gitDir, "ref: refs/heads/alias\n");
        // alias points to another symref, which points to the SHA.
        writeLooseRefRaw(gitDir, "refs/heads/alias", "ref: refs/heads/main\n");
        writeLooseRef(gitDir, "refs/heads/main", SHA_MAIN);

        GitProvenance provenance = reader.read(root).orElseThrow();

        assertEquals(Optional.of(SHA_MAIN), provenance.commitSha());
        assertEquals(Optional.of("main"), provenance.branch());
    }

    @Test
    void symrefCycleDoesNotLoopForever(@TempDir Path root) throws IOException {
        Path gitDir = Files.createDirectory(root.resolve(".git"));
        writeHead(gitDir, "ref: refs/heads/a\n");
        writeLooseRefRaw(gitDir, "refs/heads/a", "ref: refs/heads/b\n");
        writeLooseRefRaw(gitDir, "refs/heads/b", "ref: refs/heads/a\n");

        GitProvenance provenance = reader.read(root).orElseThrow();

        // No SHA resolvable, but the reader returns cleanly (no crash / no infinite loop).
        assertTrue(provenance.commitSha().isEmpty());
    }

    @Test
    void linkedWorktreeResolvesViaGitFileAndCommondir(@TempDir Path root) throws IOException {
        // The real (common) git dir with the shared packed-refs.
        Path commonGitDir = Files.createDirectory(root.resolve("maingit"));
        writePackedRefs(commonGitDir, SHA_PACKED + " refs/heads/feature\n");

        // The per-worktree git dir, referenced by the worktree's .git FILE.
        Path worktreeGitDir = Files.createDirectories(root.resolve("worktrees").resolve("wt1"));
        writeHead(worktreeGitDir, "ref: refs/heads/feature\n");
        Files.writeString(worktreeGitDir.resolve("commondir"), "../../maingit\n", StandardCharsets.UTF_8);

        // The worktree checkout with a .git FILE pointing at the per-worktree git dir.
        Path worktree = Files.createDirectory(root.resolve("checkout"));
        Files.writeString(
                worktree.resolve(".git"),
                "gitdir: ../worktrees/wt1\n",
                StandardCharsets.UTF_8);

        GitProvenance provenance = reader.read(worktree).orElseThrow();

        assertEquals(Optional.of(SHA_PACKED), provenance.commitSha());
        assertEquals(Optional.of("feature"), provenance.branch());
    }

    @Test
    void linkedWorktreeLooseRefStillBeatsCommonPacked(@TempDir Path root) throws IOException {
        Path commonGitDir = Files.createDirectory(root.resolve("maingit"));
        writePackedRefs(commonGitDir, SHA_PACKED + " refs/heads/feature\n");

        Path worktreeGitDir = Files.createDirectories(root.resolve("worktrees").resolve("wt1"));
        writeHead(worktreeGitDir, "ref: refs/heads/feature\n");
        writeLooseRef(worktreeGitDir, "refs/heads/feature", SHA_MAIN);
        Files.writeString(worktreeGitDir.resolve("commondir"), "../../maingit\n", StandardCharsets.UTF_8);

        Path worktree = Files.createDirectory(root.resolve("checkout"));
        Files.writeString(worktree.resolve(".git"), "gitdir: ../worktrees/wt1\n", StandardCharsets.UTF_8);

        GitProvenance provenance = reader.read(worktree).orElseThrow();

        assertEquals(Optional.of(SHA_MAIN), provenance.commitSha());
    }

    @Test
    void notARepoReturnsEmpty(@TempDir Path root) {
        assertTrue(reader.read(root).isEmpty());
    }

    @Test
    void nullProjectRootReturnsEmpty() {
        assertTrue(reader.read(null).isEmpty());
    }

    @Test
    void absentHeadDoesNotCrash(@TempDir Path root) throws IOException {
        Files.createDirectory(root.resolve(".git"));
        // .git dir exists but no HEAD file.
        GitProvenance provenance = reader.read(root).orElseThrow();

        assertTrue(provenance.commitSha().isEmpty());
        assertTrue(provenance.branch().isEmpty());
        assertFalse(provenance.detached());
    }

    @Test
    void malformedHeadDoesNotCrash(@TempDir Path root) throws IOException {
        Path gitDir = Files.createDirectory(root.resolve(".git"));
        writeHead(gitDir, "this is not a valid HEAD\n");

        GitProvenance provenance = reader.read(root).orElseThrow();

        assertTrue(provenance.commitSha().isEmpty());
        assertTrue(provenance.branch().isEmpty());
        assertFalse(provenance.detached());
    }

    @Test
    void emptyHeadDoesNotCrash(@TempDir Path root) throws IOException {
        Path gitDir = Files.createDirectory(root.resolve(".git"));
        writeHead(gitDir, "\n");

        GitProvenance provenance = reader.read(root).orElseThrow();

        assertTrue(provenance.commitSha().isEmpty());
    }

    @Test
    void unresolvableSymrefReturnsBranchWithoutSha(@TempDir Path root) throws IOException {
        Path gitDir = Files.createDirectory(root.resolve(".git"));
        writeHead(gitDir, "ref: refs/heads/missing\n");
        // No loose ref, no packed-refs.

        GitProvenance provenance = reader.read(root).orElseThrow();

        assertEquals(Optional.of("missing"), provenance.branch());
        assertTrue(provenance.commitSha().isEmpty());
    }

    @Test
    void dirtyIsAlwaysUnknownInV1(@TempDir Path root) throws IOException {
        Path gitDir = Files.createDirectory(root.resolve(".git"));
        writeHead(gitDir, "ref: refs/heads/main\n");
        writeLooseRef(gitDir, "refs/heads/main", SHA_MAIN);

        GitProvenance provenance = reader.read(root).orElseThrow();

        assertTrue(provenance.dirty().isEmpty());
    }

    @Test
    void outputIsDeterministic(@TempDir Path root) throws IOException {
        Path gitDir = Files.createDirectory(root.resolve(".git"));
        writeHead(gitDir, "ref: refs/heads/main\n");
        writeLooseRef(gitDir, "refs/heads/main", SHA_MAIN);

        GitProvenance first = reader.read(root).orElseThrow();
        GitProvenance second = reader.read(root).orElseThrow();

        assertEquals(first, second);
    }

    @Test
    void noneFactoryIsWellFormedEmpty() {
        GitProvenance none = GitProvenance.none();

        assertTrue(none.commitSha().isEmpty());
        assertTrue(none.shortSha().isEmpty());
        assertTrue(none.branch().isEmpty());
        assertFalse(none.detached());
        assertTrue(none.dirty().isEmpty());
    }

    private static void writeHead(Path gitDir, String contents) throws IOException {
        Files.writeString(gitDir.resolve("HEAD"), contents, StandardCharsets.UTF_8);
    }

    private static void writeLooseRef(Path gitDir, String refPath, String sha) throws IOException {
        writeLooseRefRaw(gitDir, refPath, sha + "\n");
    }

    private static void writeLooseRefRaw(Path gitDir, String refPath, String contents) throws IOException {
        Path refFile = gitDir.resolve(refPath.replace('/', java.io.File.separatorChar));
        Files.createDirectories(refFile.getParent());
        Files.writeString(refFile, contents, StandardCharsets.UTF_8);
    }

    private static void writePackedRefs(Path gitDir, String contents) throws IOException {
        Files.writeString(gitDir.resolve("packed-refs"), contents, StandardCharsets.UTF_8);
    }
}
