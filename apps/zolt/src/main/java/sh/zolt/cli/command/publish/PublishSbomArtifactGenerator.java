package sh.zolt.cli.command.publish;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.lockfile.toml.ZoltLockfileReader;
import sh.zolt.project.ProjectConfig;
import sh.zolt.sbom.CycloneDxSbomWriter;
import sh.zolt.sbom.LicenseIndex;
import sh.zolt.sbom.LockSbomAssembler;
import sh.zolt.sbom.SbomModel;
import sh.zolt.sbom.SbomScopeSelection;
import sh.zolt.toml.ZoltTomlParser;

/**
 * Generates the lock-only CycloneDX SBOM attached by {@code zolt publish --sbom} and writes it next
 * to the generated POM as {@code <name>-<version>-cyclonedx.json}. Dependency license resolution is
 * available via {@code zolt licenses}/{@code zolt sbom}; the published artifact stays cache-free and
 * byte-reproducible (components, hashes, edges, and the config-authoritative root license).
 */
final class PublishSbomArtifactGenerator {
    private final ZoltTomlParser tomlParser = new ZoltTomlParser();
    private final ZoltLockfileReader lockfileReader = new ZoltLockfileReader();
    private final LockSbomAssembler assembler = new LockSbomAssembler();
    private final CycloneDxSbomWriter writer = new CycloneDxSbomWriter();

    Optional<Path> generate(boolean enabled, Path projectRoot, String toolVersion) {
        if (!enabled) {
            return Optional.empty();
        }
        ProjectConfig config = tomlParser.parse(projectRoot.resolve("zolt.toml"));
        ZoltLockfile lockfile = lockfileReader.read(projectRoot.resolve("zolt.lock"));
        SbomModel model = assembler.assemble(
                config, lockfile, SbomScopeSelection.requiredOnly(), Optional.empty(),
                toolVersion, LicenseIndex.empty());
        Path sbomPath = projectRoot.resolve(config.build().outputRoot()).resolve("publish")
                .resolve(config.project().name() + "-" + config.project().version() + "-cyclonedx.json")
                .normalize();
        try {
            Files.createDirectories(sbomPath.getParent());
            Files.writeString(sbomPath, writer.write(model), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return Optional.of(sbomPath);
    }
}
