package com.zolt.cli.command;

import com.zolt.build.packageplan.PackagePlanService;
import com.zolt.framework.FrameworkBuildAugmenter;
import com.zolt.framework.FrameworkPackageAugmenter;
import com.zolt.framework.FrameworkRunAugmenter;
import com.zolt.framework.FrameworkTestRunner;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import java.util.Objects;

public final class CommandServiceClusters {
    private CommandServiceClusters() {
    }

    public record CommandConfigEditServices(
            ZoltTomlParser tomlParser,
            ZoltTomlWriter tomlWriter,
            ResolveService resolveService) {
        public CommandConfigEditServices {
            Objects.requireNonNull(tomlParser, "tomlParser");
            Objects.requireNonNull(tomlWriter, "tomlWriter");
            Objects.requireNonNull(resolveService, "resolveService");
        }
    }

    public record CommandPackageFrameworkServices(
            FrameworkPackageAugmenter packageAugmenter,
            PackagePlanService packagePlanService) {
        public CommandPackageFrameworkServices {
            Objects.requireNonNull(packageAugmenter, "packageAugmenter");
            Objects.requireNonNull(packagePlanService, "packagePlanService");
        }
    }

    public record CommandBuildFrameworkServices(
            FrameworkBuildAugmenter frameworkBuildAugmenter,
            ResolveService resolveService) {
        public CommandBuildFrameworkServices {
            Objects.requireNonNull(frameworkBuildAugmenter, "frameworkBuildAugmenter");
            Objects.requireNonNull(resolveService, "resolveService");
        }
    }

    public record CommandTestFrameworkServices(
            FrameworkTestRunner frameworkTestRunner,
            ResolveService resolveService) {
        public CommandTestFrameworkServices {
            Objects.requireNonNull(frameworkTestRunner, "frameworkTestRunner");
            Objects.requireNonNull(resolveService, "resolveService");
        }
    }

    public record CommandRunFrameworkServices(
            FrameworkRunAugmenter frameworkRunAugmenter,
            ResolveService resolveService) {
        public CommandRunFrameworkServices {
            Objects.requireNonNull(frameworkRunAugmenter, "frameworkRunAugmenter");
            Objects.requireNonNull(resolveService, "resolveService");
        }
    }
}
