package com.zolt.cli.command;

import com.zolt.build.PackageException;
import com.zolt.cli.CommandProgress;
import com.zolt.cli.console.ProgressWriter;
import com.zolt.publish.PublishContext;
import com.zolt.publish.PublishDryRunFormatter;
import com.zolt.publish.PublishDryRunPlan;
import com.zolt.publish.PublishDryRunService;
import com.zolt.publish.PublishException;
import com.zolt.publish.PublishReleasePolicyService;
import com.zolt.publish.PublishUploadFormatter;
import com.zolt.publish.PublishUploadResult;
import com.zolt.publish.PublishUploadService;
import com.zolt.toml.ZoltConfigException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "publish", description = "Publish Zolt-produced artifacts to Maven-compatible repositories.")
public final class PublishCommand implements Callable<Integer> {
    private final PublishDryRunService dryRunService;
    private final PublishReleasePolicyService releasePolicyService;
    private final PublishUploadService uploadService;

    @Option(names = "--dry-run", description = "Preview target routing, artifact evidence, and blockers without uploading.")
    private boolean dryRun;

    @Option(names = "--context", description = "Apply a publish context policy. Supported values: release.")
    private PublishContext context;

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Spec
    private CommandSpec spec;

    public PublishCommand() {
        this(new PublishDryRunService(), new PublishReleasePolicyService(), new PublishUploadService());
    }

    PublishCommand(
            PublishDryRunService dryRunService,
            PublishReleasePolicyService releasePolicyService,
            PublishUploadService uploadService) {
        this.dryRunService = dryRunService;
        this.releasePolicyService = releasePolicyService;
        this.uploadService = uploadService;
    }

    @Override
    public Integer call() {
        ProgressWriter progress = CommandProgress.human(spec);
        try {
            if (context != null && !dryRun) {
                CommandFailures.printUser(spec, "Publish context policy is currently supported only with --dry-run.");
                return 1;
            }
            if (dryRun) {
                progress.start("Preparing publish dry run");
                PublishDryRunPlan plan = dryRunService.plan(workingDirectory);
                if (context == PublishContext.RELEASE) {
                    plan = releasePolicyService.apply(workingDirectory, plan);
                }
                CommandOutput.printAndFlush(spec, PublishDryRunFormatter.text(plan));
                progress.result("Prepared publish dry run");
                return plan.ok() ? 0 : 1;
            }
            progress.start("Publishing artifacts");
            PublishUploadResult result = uploadService.upload(workingDirectory);
            CommandOutput.printAndFlush(spec, PublishUploadFormatter.text(result));
            progress.result("Published artifacts");
            return 0;
        } catch (PublishException | ZoltConfigException | PackageException exception) {
            CommandFailures.printUser(spec, exception);
            return 1;
        }
    }
}
