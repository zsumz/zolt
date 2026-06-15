package com.zolt.build;

import java.util.List;
import java.util.Map;

record TestRuntimeInputs(
        TestJvmArguments jvmArguments,
        Map<String, String> environment,
        List<String> events) {
}
