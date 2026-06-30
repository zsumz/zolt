package com.zolt.build.run;

import com.zolt.build.BuildResult;

public record RunResult(
        BuildResult buildResult,
        JavaRunResult javaRunResult) {
}
