package com.zolt.build.packaging;

import com.zolt.dependency.PackageId;
import java.nio.file.Path;

public record PackageRuntimeJar(PackageId packageId, String version, Path jarPath) {
}
