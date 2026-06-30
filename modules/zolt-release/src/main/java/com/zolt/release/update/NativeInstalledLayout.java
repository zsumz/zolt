package com.zolt.release.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

record NativeInstalledLayout(
        Path binLink,
        Path linkTarget,
        Path versionsDirectory,
        String version) {
    static NativeInstalledLayout detect(Path installRoot, Path currentExecutable) throws IOException {
        Path root = installRoot.toAbsolutePath().normalize();
        Path binLink = root.resolve("bin").resolve("zolt");
        if (!Files.isSymbolicLink(binLink)) {
            throw new NativeUpdateException("zolt update only supports installer-managed native Zolt layouts. Expected " + binLink + " to be a symlink.");
        }
        Path linkTarget = Files.readSymbolicLink(binLink);
        Path linkedExecutable = binLink.getParent().resolve(linkTarget).normalize();
        Path current = currentExecutable.toAbsolutePath().normalize();
        Path currentReal = Files.exists(current) ? current.toRealPath() : current;
        Path linkedReal = Files.exists(linkedExecutable) ? linkedExecutable.toRealPath() : linkedExecutable;
        if (!current.equals(binLink) && !currentReal.equals(linkedReal)) {
            throw new NativeUpdateException("zolt update only supports the active installer-managed native Zolt executable under " + binLink + ".");
        }
        Path versions = root.resolve("versions");
        if (!linkedExecutable.startsWith(versions)) {
            throw new NativeUpdateException("zolt update only supports versioned native installs under " + versions + ".");
        }
        Path relative = versions.relativize(linkedExecutable);
        if (relative.getNameCount() < 3 || !relative.getName(1).toString().equals("bin")) {
            throw new NativeUpdateException("zolt update only supports versioned native installs under " + versions + ".");
        }
        return new NativeInstalledLayout(binLink, linkTarget, versions, relative.getName(0).toString());
    }
}
