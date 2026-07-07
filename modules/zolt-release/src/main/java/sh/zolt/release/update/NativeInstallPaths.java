package sh.zolt.release.update;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

final class NativeInstallPaths {
    private NativeInstallPaths() {
    }

    static Path ownedDirectory(Path path, String label) throws IOException {
        Path directory = path.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new NativeUpdateException(label + " must not be a symbolic link: " + directory + ".");
        }
        return directory.toRealPath().normalize();
    }

    static Path resolveUnderRoot(Path root, String child, String label) {
        Path path = root.resolve(child).normalize();
        assertUnderRoot(root, path, label);
        return path;
    }

    static void assertUnderRoot(Path root, Path path, String label) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot)) {
            throw new NativeUpdateException(label + " must stay under " + normalizedRoot + ": " + normalizedPath + ".");
        }
    }

    static void deleteIfExists(Path root, Path path, String label) throws IOException {
        assertUnderRoot(root, path, label);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    static void switchCurrent(Path binRoot, Path binLink, Path previousTarget, Path nextTarget) throws IOException {
        Path tempLink = binLink.resolveSibling(binLink.getFileName() + ".update");
        deleteIfExists(binRoot, tempLink, "native update temporary symlink");
        try {
            Files.createSymbolicLink(tempLink, nextTarget);
            try {
                Files.move(tempLink, binLink, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(tempLink, binLink, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            deleteIfExists(binRoot, tempLink, "native update temporary symlink");
            restoreLink(binRoot, binLink, previousTarget);
            throw exception;
        }
    }

    private static void restoreLink(Path binRoot, Path binLink, Path previousTarget) throws IOException {
        deleteIfExists(binRoot, binLink, "native current symlink");
        Files.createSymbolicLink(binLink, previousTarget);
    }
}
