package com.zolt.workspace;

import com.zolt.resolve.ResolveException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class WorkspaceLockfileFingerprints {
    private WorkspaceLockfileFingerprints() {
    }

    static Optional<String> aliasFingerprint(List<WorkspaceMemberResolveOutput> memberOutputs) {
        List<String> inputs = memberOutputs.stream()
                .filter(output -> output.lockfile().aliasFingerprint().isPresent())
                .sorted(Comparator.comparing(WorkspaceMemberResolveOutput::member))
                .map(output -> output.member() + "\t" + output.lockfile().aliasFingerprint().orElseThrow())
                .toList();
        if (inputs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("sha256:" + sha256((String.join("\n", inputs) + "\n").getBytes(StandardCharsets.UTF_8)));
    }

    static Optional<String> projectResolutionFingerprint(List<WorkspaceMemberResolveOutput> memberOutputs) {
        List<String> inputs = memberOutputs.stream()
                .filter(output -> output.lockfile().projectResolutionFingerprint().isPresent())
                .sorted(Comparator.comparing(WorkspaceMemberResolveOutput::member))
                .map(output -> output.member() + "\t" + output.lockfile().projectResolutionFingerprint().orElseThrow())
                .toList();
        if (inputs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("sha256:" + sha256((String.join("\n", inputs) + "\n").getBytes(StandardCharsets.UTF_8)));
    }

    static List<String> projectResolutionInputFingerprints(List<WorkspaceMemberResolveOutput> memberOutputs) {
        Map<String, List<String>> inputs = new LinkedHashMap<>();
        memberOutputs.stream()
                .sorted(Comparator.comparing(WorkspaceMemberResolveOutput::member))
                .forEach(output -> output.lockfile().projectResolutionInputFingerprints().forEach(input -> {
                    int separator = input.indexOf('=');
                    if (separator <= 0 || separator == input.length() - 1) {
                        return;
                    }
                    inputs.computeIfAbsent(input.substring(0, separator), ignored -> new ArrayList<>())
                            .add(output.member() + "\t" + input.substring(separator + 1));
                }));
        return inputs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=sha256:" + sha256((String.join("\n", entry.getValue()) + "\n")
                        .getBytes(StandardCharsets.UTF_8)))
                .toList();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new ResolveException(
                    "Could not compute workspace alias fingerprint because SHA-256 is unavailable.",
                    exception);
        }
    }
}
