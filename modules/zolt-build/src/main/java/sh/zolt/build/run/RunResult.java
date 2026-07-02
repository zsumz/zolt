package sh.zolt.build.run;

import sh.zolt.build.BuildResult;

public record RunResult(
        BuildResult buildResult,
        JavaRunResult javaRunResult) {
}
