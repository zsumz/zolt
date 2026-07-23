package sh.zolt.cli.command.publish;

import sh.zolt.build.PackageException;
import sh.zolt.cli.CommandProgress;
import sh.zolt.cli.command.CommandFailures;
import sh.zolt.cli.command.CommandOutput;
import sh.zolt.cli.command.CommandProjectDirectory;
import sh.zolt.cli.console.ProgressWriter;
import sh.zolt.cli.net.CommandNetwork;
import sh.zolt.publish.CentralPortalClient;
import sh.zolt.publish.PublishCentralBundleResult;
import sh.zolt.publish.PublishCentralPublishOutcome;
import sh.zolt.publish.PublishCentralPublishService;
import sh.zolt.publish.PublishCentralReadinessService;
import sh.zolt.publish.PublishCentralRequirement;
import sh.zolt.publish.PublishCentralUploadFormatter;
import sh.zolt.publish.PublishCentralUploadResult;
import sh.zolt.publish.PublishContext;
import sh.zolt.publish.PublishDryRunFormatter;
import sh.zolt.publish.PublishDryRunPlan;
import sh.zolt.publish.PublishDryRunService;
import sh.zolt.publish.PublishException;
import sh.zolt.publish.PublishReleasePolicyService;
import sh.zolt.publish.PublishUploadFormatter;
import sh.zolt.publish.PublishUploadResult;
import sh.zolt.publish.PublishUploadService;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.lockfile.toml.LockfileReadException;
import sh.zolt.lockfile.toml.ZoltLockfileReader;
import sh.zolt.project.ProjectConfig;
import sh.zolt.sbom.CycloneDxSbomWriter;
import sh.zolt.sbom.LicenseIndex;
import sh.zolt.sbom.LockSbomAssembler;
import sh.zolt.sbom.SbomModel;
import sh.zolt.sbom.SbomScopeSelection;
import sh.zolt.cli.ZoltCli;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.ZoltTomlParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "publish",
        description = "Publish Zolt-produced artifacts to Maven-compatible repositories.")
public final class PublishCommand implements Callable<Integer> {
    private final PublishDryRunService dryRunService;
    private final PublishReleasePolicyService releasePolicyService;
    private final PublishUploadService uploadService;
    private final PublishCentralReadinessService centralReadinessService;
    private final PublishCentralPublishService centralPublishService;
    private final ZoltTomlParser tomlParser = new ZoltTomlParser();
    private final ZoltLockfileReader lockfileReader = new ZoltLockfileReader();
    private final LockSbomAssembler sbomAssembler = new LockSbomAssembler();
    private final CycloneDxSbomWriter sbomWriter = new CycloneDxSbomWriter();

    @Option(names = "--dry-run", description = "Preview target routing, artifact evidence, and blockers without uploading.")
    private boolean dryRun;

    @Option(names = "--sbom", description = "Attach a CycloneDX SBOM (classifier cyclonedx, extension json) to the publish.")
    private boolean sbom;

    @Option(names = "--central", description = "Target Maven Central: publish a signed bundle, or with --dry-run report readiness and assemble the bundle locally.")
    private boolean central;

    @Option(names = "--context", description = "Apply a publish context policy. Supported values: release.")
    private PublishContext context;

    @Option(names = "--wait", description = "After a --central upload, poll the deployment until it reaches a terminal state (published, failed, or — for user-managed — validated).")
    private boolean wait;

    @Option(names = "--wait-timeout", paramLabel = "<seconds>", defaultValue = "300",
            description = "Maximum seconds to wait for a terminal state when --wait is set (default: 300).")
    private long waitTimeoutSeconds;

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Spec
    private CommandSpec spec;

    public PublishCommand() {
        this(
                new PublishDryRunService(),
                new PublishReleasePolicyService(),
                new PublishUploadService(CommandNetwork.repositoryClient()),
                new PublishCentralReadinessService(),
                new PublishCentralPublishService(new CentralPortalClient(CommandNetwork.defaultTransport())));
    }

    PublishCommand(
            PublishDryRunService dryRunService,
            PublishReleasePolicyService releasePolicyService,
            PublishUploadService uploadService,
            PublishCentralReadinessService centralReadinessService,
            PublishCentralPublishService centralPublishService) {
        this.dryRunService = dryRunService;
        this.releasePolicyService = releasePolicyService;
        this.uploadService = uploadService;
        this.centralReadinessService = centralReadinessService;
        this.centralPublishService = centralPublishService;
    }

    @Override
    public Integer call() {
        ProgressWriter progress = CommandProgress.human(spec);
        Path projectRoot = projectDirectory.path();
        try {
            if (context != null && !dryRun) {
                CommandFailures.printUser(spec, "Publish context policy is currently supported only with --dry-run.");
                return 1;
            }
            if (wait && (dryRun || !central)) {
                CommandFailures.printUser(spec,
                        "The --wait flag applies only to a live Maven Central publish; use it with --central and without --dry-run.");
                return 1;
            }
            if (wait && waitTimeoutSeconds <= 0) {
                CommandFailures.printUser(spec, "--wait-timeout must be a positive number of seconds.");
                return 1;
            }
            Optional<Path> sbomFile = generateSbom(projectRoot);
            if (central && !dryRun) {
                progress.start("Publishing to Maven Central");
                PublishDryRunPlan plan = dryRunService.plan(projectRoot, false, sbomFile);
                if (!plan.ok()) {
                    CommandOutput.printAndFlush(spec, PublishDryRunFormatter.text(plan));
                    return 1;
                }
                List<PublishCentralRequirement> readiness = centralReadinessService.evaluate(projectRoot, plan);
                if (!readiness.stream().allMatch(PublishCentralRequirement::satisfied)) {
                    CommandOutput.printAndFlush(spec, PublishDryRunFormatter.centralReadiness(readiness));
                    return 1;
                }
                Optional<Duration> waitTimeout = wait
                        ? Optional.of(Duration.ofSeconds(waitTimeoutSeconds))
                        : Optional.empty();
                PublishCentralUploadResult centralResult = centralPublishService.publish(projectRoot, plan, waitTimeout);
                CommandOutput.printAndFlush(spec, PublishCentralUploadFormatter.text(centralResult));
                progress.result(centralProgressResult(centralResult.outcome()));
                return 0;
            }
            if (dryRun) {
                progress.start("Preparing publish dry run");
                PublishDryRunPlan plan = dryRunService.plan(projectRoot, !central, sbomFile);
                if (context == PublishContext.RELEASE) {
                    plan = releasePolicyService.apply(projectRoot, plan);
                }
                StringBuilder output = new StringBuilder(PublishDryRunFormatter.text(plan));
                boolean centralReady = true;
                if (central) {
                    List<PublishCentralRequirement> readiness = centralReadinessService.evaluate(projectRoot, plan);
                    output.append(PublishDryRunFormatter.centralReadiness(readiness));
                    centralReady = readiness.stream().allMatch(PublishCentralRequirement::satisfied);
                    if (plan.ok()) {
                        PublishCentralBundleResult bundle = centralPublishService.assembleBundle(projectRoot, plan);
                        output.append(PublishDryRunFormatter.centralBundle(
                                displayPath(projectRoot, bundle.bundlePath()), bundle.entries()));
                    }
                }
                CommandOutput.printAndFlush(spec, output.toString());
                progress.result("Prepared publish dry run");
                return plan.ok() && centralReady ? 0 : 1;
            }
            progress.start("Publishing artifacts");
            PublishUploadResult result = uploadService.upload(projectRoot, sbomFile);
            CommandOutput.printAndFlush(spec, PublishUploadFormatter.text(result));
            progress.result("Published artifacts");
            return 0;
        } catch (PublishException | ZoltConfigException | PackageException | LockfileReadException exception) {
            CommandFailures.printUser(spec, exception);
            return 1;
        } catch (UncheckedIOException exception) {
            CommandFailures.printUser(spec, "Could not write the SBOM artifact for publishing: " + exception.getMessage());
            return 1;
        }
    }

    /**
     * Generates a lock-only CycloneDX SBOM (components, hashes, edges, and the config-authoritative
     * root license) and writes it next to the generated POM as {@code <name>-<version>-cyclonedx.json}.
     * Dependency license resolution is available via {@code zolt licenses}/{@code zolt sbom}; the
     * published artifact stays cache-free and byte-reproducible.
     */
    private Optional<Path> generateSbom(Path projectRoot) {
        if (!sbom) {
            return Optional.empty();
        }
        ProjectConfig config = tomlParser.parse(projectRoot.resolve("zolt.toml"));
        ZoltLockfile lockfile = lockfileReader.read(projectRoot.resolve("zolt.lock"));
        SbomModel model = sbomAssembler.assemble(
                config, lockfile, SbomScopeSelection.requiredOnly(), Optional.empty(),
                ZoltCli.version(), LicenseIndex.empty());
        Path sbomPath = projectRoot.resolve(config.build().outputRoot()).resolve("publish")
                .resolve(config.project().name() + "-" + config.project().version() + "-cyclonedx.json")
                .normalize();
        try {
            Files.createDirectories(sbomPath.getParent());
            Files.writeString(sbomPath, sbomWriter.write(model), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return Optional.of(sbomPath);
    }

    private static String centralProgressResult(PublishCentralPublishOutcome outcome) {
        return switch (outcome) {
            case UPLOADED, PUBLISHED -> "Published to Maven Central";
            case AWAITING_MANUAL_RELEASE -> "Validated on the Central Portal — release it to finish publishing";
        };
    }

    private static String displayPath(Path root, Path path) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(normalizedRoot)
                ? normalizedRoot.relativize(normalized).toString()
                : normalized.toString();
    }
}
