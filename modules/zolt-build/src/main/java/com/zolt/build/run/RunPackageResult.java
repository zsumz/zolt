package com.zolt.build.run;

import com.zolt.build.packaging.PackageResult;

public record RunPackageResult(
        PackageResult packageResult,
        JavaRunResult javaRunResult) {
}
