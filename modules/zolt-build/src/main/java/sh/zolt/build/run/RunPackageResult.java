package sh.zolt.build.run;

import sh.zolt.build.packaging.PackageResult;

public record RunPackageResult(
        PackageResult packageResult,
        JavaRunResult javaRunResult) {
}
