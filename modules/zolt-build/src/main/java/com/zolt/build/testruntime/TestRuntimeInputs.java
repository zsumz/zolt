package com.zolt.build.testruntime;

import java.util.List;
import java.util.Map;

record TestRuntimeInputs(
        TestJvmArguments jvmArguments,
        Map<String, String> environment,
        List<String> events) {
}
