package com.zolt.cli.command;

import com.zolt.build.PackageException;
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
    @Option(names = "--dry-run", description = "Preview target routing, artifact evidence, and blockers without uploading.")
    private boolean dryRun;

    @Option(names = "--context", description = "Apply a publish context policy. Supported values: release.")
    private PublishContext context;

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        try {
            if (context != null && !dryRun) {
                CommandFailures.printUser(spec, "Publish context policy is currently supported only with --dry-run.");
                return 1;
            }
            if (dryRun) {
                PublishDryRunPlan plan = new PublishDryRunService().plan(workingDirectory);
                if (context == PublishContext.RELEASE) {
                    plan = new PublishReleasePolicyService().apply(workingDirectory, plan);
                }
                CommandOutput.printAndFlush(spec, PublishDryRunFormatter.text(plan));
                return plan.ok() ? 0 : 1;
            }
            PublishUploadResult result = new PublishUploadService().upload(workingDirectory);
            CommandOutput.printAndFlush(spec, PublishUploadFormatter.text(result));
            return 0;
        } catch (PublishException | ZoltConfigException | PackageException exception) {
            CommandFailures.printUser(spec, exception);
            return 1;
        }
    }
}
