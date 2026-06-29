package com.zolt.build.packaging;

import com.zolt.build.PackageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PackageRuntimeJars {
    private PackageRuntimeJars() {
    }

    public static String nestedJarName(PackageRuntimeJar runtimeJar) {
        Path fileName = runtimeJar.jarPath().getFileName();
        if (fileName != null && !fileName.toString().isBlank()) {
            return fileName.toString();
        }
        return runtimeJar.packageId().toString().replace(':', '-') + "-" + runtimeJar.version() + ".jar";
    }

    public static byte[] read(PackageRuntimeJar runtimeJar) throws IOException {
        if (!Files.isRegularFile(runtimeJar.jarPath())) {
            throw new PackageException(
                    "Runtime dependency jar for "
                            + runtimeJar.packageId()
                            + " is missing at "
                            + runtimeJar.jarPath()
                            + ". Run `zolt resolve` to refresh the artifact cache and retry.");
        }
        return Files.readAllBytes(runtimeJar.jarPath());
    }
}
