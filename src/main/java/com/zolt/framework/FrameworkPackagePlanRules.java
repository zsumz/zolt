package com.zolt.framework;

import com.zolt.lockfile.LockPackage;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;

public interface FrameworkPackagePlanRules {
    boolean supports(PackageMode mode);

    FrameworkPackagePlanDependency dependency(LockPackage lockPackage);

    Path archivePath(Path projectRoot, ProjectConfig config);

    String applicationLayout();
}
