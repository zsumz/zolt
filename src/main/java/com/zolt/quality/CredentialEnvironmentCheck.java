package com.zolt.quality;

import java.util.List;

record CredentialEnvironmentCheck(List<String> missing, List<String> placeholders) {
    CredentialEnvironmentCheck {
        missing = List.copyOf(missing);
        placeholders = List.copyOf(placeholders);
    }
}
