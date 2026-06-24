package com.zolt.cli.command;

import com.zolt.build.PackagePlanService;
import com.zolt.framework.FrameworkBuildAugmenter;
import com.zolt.framework.FrameworkPackageAugmenter;
import com.zolt.framework.FrameworkRunAugmenter;
import com.zolt.framework.FrameworkTestRunner;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import java.util.Objects;

record CommandConfigEditServices(
        ZoltTomlParser tomlParser,
        ZoltTomlWriter tomlWriter,
        ResolveService resolveService) {
    CommandConfigEditServices {
        Objects.requireNonNull(tomlParser, "tomlParser");
        Objects.requireNonNull(tomlWriter, "tomlWriter");
        Objects.requireNonNull(resolveService, "resolveService");
    }
}

record CommandPackageFrameworkServices(
        FrameworkPackageAugmenter packageAugmenter,
        PackagePlanService packagePlanService) {
    CommandPackageFrameworkServices {
        Objects.requireNonNull(packageAugmenter, "packageAugmenter");
        Objects.requireNonNull(packagePlanService, "packagePlanService");
    }
}

record CommandBuildFrameworkServices(
        FrameworkBuildAugmenter frameworkBuildAugmenter,
        ResolveService resolveService) {
    CommandBuildFrameworkServices {
        Objects.requireNonNull(frameworkBuildAugmenter, "frameworkBuildAugmenter");
        Objects.requireNonNull(resolveService, "resolveService");
    }
}

record CommandTestFrameworkServices(
        FrameworkTestRunner frameworkTestRunner,
        ResolveService resolveService) {
    CommandTestFrameworkServices {
        Objects.requireNonNull(frameworkTestRunner, "frameworkTestRunner");
        Objects.requireNonNull(resolveService, "resolveService");
    }
}

record CommandRunFrameworkServices(
        FrameworkRunAugmenter frameworkRunAugmenter,
        ResolveService resolveService) {
    CommandRunFrameworkServices {
        Objects.requireNonNull(frameworkRunAugmenter, "frameworkRunAugmenter");
        Objects.requireNonNull(resolveService, "resolveService");
    }
}
