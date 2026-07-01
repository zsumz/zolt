package com.zolt.provenance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Reads git provenance by parsing the {@code .git} directory directly with pure {@link java.nio.file}
 * I/O — no {@code git} subprocess, no reflection, native-image safe.
 *
 * <p>Algorithm:
 *
 * <ol>
 *   <li>Locate the git dir: {@code <root>/.git} is a directory → use it; {@code <root>/.git} is a file
 *       → parse {@code gitdir: <path>} (linked worktree; path may be relative) and read the optional
 *       {@code commondir} to find the shared {@code packed-refs}. Neither → not a repo → empty.
 *   <li>Read {@code HEAD}: {@code ref: refs/heads/<b>} is a symref; a raw 40/64-hex line is a detached
 *       HEAD.
 *   <li>Resolve a symref loose-ref-first ({@code <gitDir>/<refPath>}) — the loose ref wins over
 *       {@code packed-refs}; if absent, scan {@code packed-refs} (skipping {@code #} comment and
 *       {@code ^} peel lines) for the line ending in {@code  <refPath>}. A chained symref is followed
 *       once more with a bounded cycle guard.
 *   <li>Short SHA = first 12 chars.
 * </ol>
 *
 * <p>Never throws on a normal filesystem; any unexpected {@link IOException} degrades to
 * {@link Optional#empty()} (provenance is omitted cleanly rather than failing the build).
 */
public final class GitProvenanceReader {

    private static final int SHORT_SHA_LENGTH = 12;
    private static final int MAX_SYMREF_HOPS = 8;

    /**
     * Reads git provenance for {@code projectRoot}. Returns {@link Optional#empty()} when the path is
     * not a git repository; a well-formed {@link GitProvenance} otherwise (with empty fields where a
     * value could not be resolved).
     */
    public Optional<GitProvenance> read(Path projectRoot) {
        if (projectRoot == null) {
            return Optional.empty();
        }
        try {
            return readInternal(projectRoot);
        } catch (IOException | UncheckedIOException e) {
            // A read failure on an otherwise-present repo should never fail the build: omit cleanly.
            return Optional.empty();
        }
    }

    private Optional<GitProvenance> readInternal(Path projectRoot) throws IOException {
        Optional<GitDir> located = locateGitDir(projectRoot);
        if (located.isEmpty()) {
            return Optional.empty();
        }
        GitDir gitDir = located.get();

        Path headFile = gitDir.gitDir().resolve("HEAD");
        if (!Files.isRegularFile(headFile)) {
            // A git dir with no readable HEAD: it is a repo, but we cannot say more.
            return Optional.of(GitProvenance.none());
        }
        String head = readTrimmed(headFile);
        if (head.isEmpty()) {
            return Optional.of(GitProvenance.none());
        }

        if (head.startsWith("ref:")) {
            String refPath = head.substring("ref:".length()).trim();
            return Optional.of(resolveSymref(gitDir, refPath));
        }

        if (isHex(head)) {
            String sha = head.toLowerCase();
            return Optional.of(new GitProvenance(
                    Optional.of(sha), Optional.of(shortSha(sha)), Optional.empty(), true, Optional.empty()));
        }

        // Present but unrecognizable HEAD: it is a repo, but nothing to report.
        return Optional.of(GitProvenance.none());
    }

    /** Follows a symref (and any chained symref) to a SHA, loose-ref-first then packed-refs. */
    private GitProvenance resolveSymref(GitDir gitDir, String initialRefPath) throws IOException {
        String refPath = initialRefPath;
        Optional<String> branch = branchName(refPath);

        for (int hop = 0; hop < MAX_SYMREF_HOPS; hop++) {
            // 1. Loose ref wins over packed-refs.
            Path looseRef = gitDir.gitDir().resolve(toRelative(refPath));
            if (Files.isRegularFile(looseRef)) {
                String contents = readTrimmed(looseRef);
                if (contents.startsWith("ref:")) {
                    // Chained symref: follow once more, bounded by the hop guard.
                    refPath = contents.substring("ref:".length()).trim();
                    branch = branchName(refPath);
                    continue;
                }
                if (isHex(contents)) {
                    return branchProvenance(branch, contents.toLowerCase());
                }
                // Malformed loose ref content: give up gracefully.
                return new GitProvenance(
                        Optional.empty(), Optional.empty(), branch, false, Optional.empty());
            }

            // 2. Fall back to packed-refs (in the common dir for linked worktrees).
            Optional<String> packed = lookupPackedRef(gitDir.commonDir(), refPath);
            if (packed.isPresent()) {
                return branchProvenance(branch, packed.get().toLowerCase());
            }

            // Unresolvable ref: return the branch we know but no SHA.
            return new GitProvenance(Optional.empty(), Optional.empty(), branch, false, Optional.empty());
        }
        // Cycle guard tripped.
        return new GitProvenance(Optional.empty(), Optional.empty(), branch, false, Optional.empty());
    }

    private GitProvenance branchProvenance(Optional<String> branch, String sha) {
        return new GitProvenance(
                Optional.of(sha), Optional.of(shortSha(sha)), branch, false, Optional.empty());
    }

    /** Scans {@code packed-refs} for the line ending in {@code  <refPath>}; skips comment/peel lines. */
    private Optional<String> lookupPackedRef(Path commonDir, String refPath) throws IOException {
        Path packedRefs = commonDir.resolve("packed-refs");
        if (!Files.isRegularFile(packedRefs)) {
            return Optional.empty();
        }
        List<String> lines = Files.readAllLines(packedRefs, StandardCharsets.UTF_8);
        String suffix = " " + refPath;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("^")) {
                continue;
            }
            if (line.endsWith(suffix)) {
                int space = line.indexOf(' ');
                if (space > 0) {
                    String sha = line.substring(0, space).strip();
                    if (isHex(sha)) {
                        return Optional.of(sha);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Locates the git dir and the common dir (which holds the shared {@code packed-refs}). */
    private Optional<GitDir> locateGitDir(Path projectRoot) throws IOException {
        Path dotGit = projectRoot.resolve(".git");
        if (Files.isDirectory(dotGit)) {
            return Optional.of(new GitDir(dotGit.normalize(), dotGit.normalize()));
        }
        if (Files.isRegularFile(dotGit)) {
            String contents = readTrimmed(dotGit);
            String gitdirLine = firstLineStartingWith(contents, "gitdir:");
            if (gitdirLine == null) {
                return Optional.empty();
            }
            String rawPath = gitdirLine.substring("gitdir:".length()).trim();
            if (rawPath.isEmpty()) {
                return Optional.empty();
            }
            Path linkedGitDir = resolveMaybeRelative(projectRoot, rawPath);
            if (!Files.isDirectory(linkedGitDir)) {
                return Optional.empty();
            }
            Path commonDir = resolveCommonDir(linkedGitDir);
            return Optional.of(new GitDir(linkedGitDir, commonDir));
        }
        return Optional.empty();
    }

    /** Reads {@code <gitDir>/commondir} if present to locate the shared refs; else the git dir itself. */
    private Path resolveCommonDir(Path gitDir) throws IOException {
        Path commonDirFile = gitDir.resolve("commondir");
        if (Files.isRegularFile(commonDirFile)) {
            String rawCommon = readTrimmed(commonDirFile);
            if (!rawCommon.isEmpty()) {
                return resolveMaybeRelative(gitDir, rawCommon);
            }
        }
        return gitDir;
    }

    private static Path resolveMaybeRelative(Path base, String rawPath) {
        Path candidate = Path.of(rawPath);
        if (candidate.isAbsolute()) {
            return candidate.normalize();
        }
        return base.resolve(candidate).normalize();
    }

    private static Optional<String> branchName(String refPath) {
        String prefix = "refs/heads/";
        if (refPath.startsWith(prefix) && refPath.length() > prefix.length()) {
            return Optional.of(refPath.substring(prefix.length()));
        }
        return Optional.empty();
    }

    /** Converts a POSIX-style ref path to a platform-correct relative path under the git dir. */
    private static Path toRelative(String refPath) {
        String[] segments = refPath.split("/");
        Path result = Path.of(segments[0]);
        for (int i = 1; i < segments.length; i++) {
            result = result.resolve(segments[i]);
        }
        return result;
    }

    private static String shortSha(String sha) {
        return sha.length() <= SHORT_SHA_LENGTH ? sha : sha.substring(0, SHORT_SHA_LENGTH);
    }

    private static boolean isHex(String value) {
        int len = value.length();
        if (len != 40 && len != 64) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static String firstLineStartingWith(String contents, String prefix) {
        for (String line : contents.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.startsWith(prefix)) {
                return trimmed;
            }
        }
        return null;
    }

    private static String readTrimmed(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8).strip();
    }

    /** A resolved git dir plus its common dir (equal for a normal repo, shared for a linked worktree). */
    private record GitDir(Path gitDir, Path commonDir) {}
}
