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
import sh.zolt.toml.ZoltConfigException;
import java.nio.file.Path;
import java.util.List;
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

    @Option(names = "--dry-run", description = "Preview target routing, artifact evidence, and blockers without uploading.")
    private boolean dryRun;

    @Option(names = "--central", description = "Target Maven Central: publish a signed bundle, or with --dry-run report readiness and assemble the bundle locally.")
    private boolean central;

    @Option(names = "--context", description = "Apply a publish context policy. Supported values: release.")
    private PublishContext context;

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
            if (central && !dryRun) {
                progress.start("Publishing to Maven Central");
                PublishDryRunPlan plan = dryRunService.plan(projectRoot, false);
                if (!plan.ok()) {
                    CommandOutput.printAndFlush(spec, PublishDryRunFormatter.text(plan));
                    return 1;
                }
                List<PublishCentralRequirement> readiness = centralReadinessService.evaluate(projectRoot, plan);
                if (!readiness.stream().allMatch(PublishCentralRequirement::satisfied)) {
                    CommandOutput.printAndFlush(spec, PublishDryRunFormatter.centralReadiness(readiness));
                    return 1;
                }
                PublishCentralUploadResult centralResult = centralPublishService.publish(projectRoot, plan);
                CommandOutput.printAndFlush(spec, PublishCentralUploadFormatter.text(centralResult));
                progress.result("Published to Maven Central");
                return 0;
            }
            if (dryRun) {
                progress.start("Preparing publish dry run");
                PublishDryRunPlan plan = dryRunService.plan(projectRoot, !central);
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
            PublishUploadResult result = uploadService.upload(projectRoot);
            CommandOutput.printAndFlush(spec, PublishUploadFormatter.text(result));
            progress.result("Published artifacts");
            return 0;
        } catch (PublishException | ZoltConfigException | PackageException exception) {
            CommandFailures.printUser(spec, exception);
            return 1;
        }
    }

    private static String displayPath(Path root, Path path) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(normalizedRoot)
                ? normalizedRoot.relativize(normalized).toString()
                : normalized.toString();
    }
}
