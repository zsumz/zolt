package com.zolt.build;

public record RunPackageResult(
        PackageResult packageResult,
        JavaRunResult javaRunResult) {
}
