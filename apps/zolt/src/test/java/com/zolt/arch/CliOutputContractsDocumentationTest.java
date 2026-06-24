package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

final class CliOutputContractsDocumentationTest {
    @Test
    void outputContractDocumentNamesMachineReadableCompatibilityPromises() throws IOException {
        String contracts = Files.readString(RepositoryPaths.root().resolve("docs/cli-output-contracts.md"));

        assertTrue(contracts.contains("`zolt plan --format json`"));
        assertTrue(contracts.contains("`zolt tree --format json`"));
        assertTrue(contracts.contains("`zolt why --format json`"));
        assertTrue(contracts.contains("`zolt ide model --format json`"));
        assertTrue(contracts.contains("`zolt check --format json`"));
        assertTrue(contracts.contains("`zolt explain --format json`"));
        assertTrue(contracts.contains("`zolt package --plan --format json`"));
        assertTrue(contracts.contains("`zolt publish --dry-run`"));
        assertTrue(contracts.contains("`--color=always`"));
        assertTrue(contracts.contains("`--progress=always`"));
        assertTrue(contracts.contains("stdout formats remain ANSI-free and progress-free"));
        assertTrue(contracts.contains("Removing or renaming a promised JSON field requires a followUp"));
    }

    @Test
    void docsIndexLinksOutputContracts() throws IOException {
        String docsIndex = Files.readString(RepositoryPaths.root().resolve("docs/README.md"));

        assertTrue(docsIndex.contains("`cli-output-contracts.md`"));
    }
}
