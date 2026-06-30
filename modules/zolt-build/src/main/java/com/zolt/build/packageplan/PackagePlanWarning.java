package com.zolt.build.packageplan;

import com.zolt.build.PackageException;

public record PackagePlanWarning(
        String code,
        String subject,
        String ruleName,
        String message,
        String nextStep) {
    public PackagePlanWarning {
        code = requireNonBlank(code, "Package plan warning code is required.");
        subject = requireNonBlank(subject, "Package plan warning subject is required.");
        ruleName = requireNonBlank(ruleName, "Package plan warning rule name is required.");
        message = requireNonBlank(message, "Package plan warning message is required.");
        nextStep = requireNonBlank(nextStep, "Package plan warning next step is required.");
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PackageException(message);
        }
        return value;
    }
}
