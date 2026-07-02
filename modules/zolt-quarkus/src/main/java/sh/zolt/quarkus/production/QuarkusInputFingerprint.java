package sh.zolt.quarkus.production;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.quarkus.QuarkusPlanException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

public final class QuarkusInputFingerprint {
    public String fingerprint(Path applicationClasses, ZoltLockfile lockfile) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateText(digest, "zolt-quarkus-input-v1\n");
            applicationClasses(digest, applicationClasses);
            lockfilePackages(digest, lockfile);
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new QuarkusPlanException(
                    "Could not fingerprint Quarkus application classes at "
                            + applicationClasses
                            + ". Check that build output is readable.");
        } catch (NoSuchAlgorithmException exception) {
            throw new QuarkusPlanException("Could not fingerprint Quarkus inputs because SHA-256 is unavailable.");
        }
    }

    private static void applicationClasses(MessageDigest digest, Path applicationClasses) throws IOException {
        updateText(digest, "application-classes\n");
        if (!Files.isDirectory(applicationClasses)) {
            updateText(digest, "missing\n");
            return;
        }

        try (java.util.stream.Stream<Path> paths = Files.walk(applicationClasses)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> applicationClasses.relativize(path).toString()))
                    .toList()) {
                updateText(digest, "file:");
                updateText(digest, applicationClasses.relativize(path).toString().replace('\\', '/'));
                updateText(digest, "\n");
                digest.update(Files.readAllBytes(path));
                updateText(digest, "\n");
            }
        }
    }

    private static void lockfilePackages(MessageDigest digest, ZoltLockfile lockfile) {
        lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.scope().entersMainRuntimeClasspath()
                        || lockPackage.scope() == DependencyScope.QUARKUS_DEPLOYMENT)
                .sorted(Comparator.comparing(QuarkusInputFingerprint::packageKey))
                .forEach(lockPackage -> lockPackage(digest, lockPackage));
    }

    private static void lockPackage(MessageDigest digest, LockPackage lockPackage) {
        updateText(digest, "package:");
        updateText(digest, packageKey(lockPackage));
        updateText(digest, "\njar:");
        updateText(digest, lockPackage.jar().orElse(""));
        updateText(digest, "\njarSha256:");
        updateText(digest, lockPackage.jarSha256().orElse(""));
        updateText(digest, "\ndependencies:");
        lockPackage.dependencies().stream()
                .sorted()
                .forEach(dependency -> {
                    updateText(digest, dependency);
                    updateText(digest, ";");
                });
        updateText(digest, "\n");
    }

    private static String packageKey(LockPackage lockPackage) {
        return lockPackage.scope().lockfileName()
                + ":"
                + lockPackage.packageId()
                + ":"
                + lockPackage.version();
    }

    private static void updateText(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }
}
