package com.zolt.build;

import com.zolt.resolve.PackageId;
import java.nio.file.Path;

record PackageRuntimeJar(PackageId packageId, String version, Path jarPath) {
}
