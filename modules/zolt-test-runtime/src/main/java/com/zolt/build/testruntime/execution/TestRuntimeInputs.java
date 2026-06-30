package com.zolt.build.testruntime.execution;

import com.zolt.test.runtime.TestJvmArguments;
import java.util.List;
import java.util.Map;

record TestRuntimeInputs(
        TestJvmArguments jvmArguments,
        Map<String, String> environment,
        List<String> events) {
}
