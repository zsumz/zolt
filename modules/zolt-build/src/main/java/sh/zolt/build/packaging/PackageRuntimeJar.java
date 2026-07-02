package sh.zolt.build.packaging;

import sh.zolt.dependency.PackageId;
import java.nio.file.Path;

public record PackageRuntimeJar(PackageId packageId, String version, Path jarPath) {
}
