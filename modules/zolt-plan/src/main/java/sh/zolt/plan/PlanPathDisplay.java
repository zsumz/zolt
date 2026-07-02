package sh.zolt.plan;

import java.nio.file.FileSystems;
import java.nio.file.Path;

final class PlanPathDisplay {
    private PlanPathDisplay() {
    }

    static String displayPath(Path root, Path path) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(normalizedRoot)) {
            return normalizedRoot.relativize(normalized)
                    .toString()
                    .replace(FileSystems.getDefault().getSeparator(), "/");
        }
        return normalized.toString();
    }
}
