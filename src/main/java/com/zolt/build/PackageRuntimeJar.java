package com.zolt.build;

import com.zolt.dependency.PackageId;
import java.nio.file.Path;

record PackageRuntimeJar(PackageId packageId, String version, Path jarPath) {
}
