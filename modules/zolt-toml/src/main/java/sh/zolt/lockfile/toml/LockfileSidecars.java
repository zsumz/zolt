package sh.zolt.lockfile.toml;

public final class LockfileSidecars {
    private LockfileSidecars() {
    }

    public static String withJavaToolchainBlocksFromExisting(
            String dependencyLockfile,
            String existingLockfile) {
        String javaToolchains = javaToolchainBlocks(existingLockfile);
        if (javaToolchains.isBlank()) {
            return dependencyLockfile;
        }
        return dependencyLockfile.stripTrailing() + "\n\n" + javaToolchains.strip() + "\n";
    }

    public static String canonicalDependencyLockfile(String content) {
        return withoutJavaToolchainBlocks(content).stripTrailing() + "\n";
    }

    private static String withoutJavaToolchainBlocks(String content) {
        StringBuilder output = new StringBuilder();
        boolean skipping = false;
        for (String line : safeLines(content)) {
            String trimmed = line.strip();
            if ("[[toolchain.java]]".equals(trimmed)) {
                skipping = true;
                continue;
            }
            if (skipping && trimmed.startsWith("[")) {
                skipping = false;
            }
            if (!skipping) {
                output.append(line).append('\n');
            }
        }
        return output.toString();
    }

    private static String javaToolchainBlocks(String content) {
        StringBuilder output = new StringBuilder();
        boolean copying = false;
        for (String line : safeLines(content)) {
            String trimmed = line.strip();
            if ("[[toolchain.java]]".equals(trimmed)) {
                copying = true;
            } else if (copying && trimmed.startsWith("[")) {
                copying = false;
            }
            if (copying) {
                output.append(line).append('\n');
            }
        }
        return output.toString();
    }

    private static java.util.List<String> safeLines(String content) {
        return content == null ? java.util.List.of() : content.lines().toList();
    }
}
