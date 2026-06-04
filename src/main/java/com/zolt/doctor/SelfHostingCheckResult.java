package com.zolt.doctor;

import java.util.List;

public record SelfHostingCheckResult(
        List<SelfHostingCheck> checks) {
    public SelfHostingCheckResult {
        checks = List.copyOf(checks);
    }

    public boolean ok() {
        return checks.stream().allMatch(SelfHostingCheck::ok);
    }

    public record SelfHostingCheck(
            String name,
            boolean ok,
            String message) {
    }
}
