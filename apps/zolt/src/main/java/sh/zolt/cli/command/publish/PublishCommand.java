package sh.zolt.cli.command.publish;

import sh.zolt.build.PackageException;
import sh.zolt.cli.CommandProgress;
import sh.zolt.cli.command.CommandFailures;
import sh.zolt.cli.command.CommandOutput;
import sh.zolt.cli.command.CommandProjectDirectory;
import sh.zolt.cli.console.ProgressWriter;
import sh.zolt.cli.net.CommandNetwork;
import sh.zolt.publish.PublishCentralReadinessService;
import sh.zolt.publish.PublishCentralRequirement;
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

    @Option(names = "--dry-run", description = "Preview target routing, artifact evidence, and blockers without uploading.")
    private boolean dryRun;

    @Option(names = "--central", description = "Report Maven Central publishing readiness. Use with --dry-run.")
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
                new PublishCentralReadinessService());
    }

    PublishCommand(
            PublishDryRunService dryRunService,
            PublishReleasePolicyService releasePolicyService,
            PublishUploadService uploadService,
            PublishCentralReadinessService centralReadinessService) {
        this.dryRunService = dryRunService;
        this.releasePolicyService = releasePolicyService;
        this.uploadService = uploadService;
        this.centralReadinessService = centralReadinessService;
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
                CommandFailures.printUser(spec, "Maven Central readiness check is currently supported only with --dry-run.");
                return 1;
            }
            if (dryRun) {
                progress.start("Preparing publish dry run");
                PublishDryRunPlan plan = dryRunService.plan(projectRoot);
                if (context == PublishContext.RELEASE) {
                    plan = releasePolicyService.apply(projectRoot, plan);
                }
                StringBuilder output = new StringBuilder(PublishDryRunFormatter.text(plan));
                boolean centralReady = true;
                if (central) {
                    List<PublishCentralRequirement> readiness = centralReadinessService.evaluate(projectRoot, plan);
                    output.append(PublishDryRunFormatter.centralReadiness(readiness));
                    centralReady = readiness.stream().allMatch(PublishCentralRequirement::satisfied);
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
}
