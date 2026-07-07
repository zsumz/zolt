package sh.zolt.cli;

import sh.zolt.project.ProjectVersionOverride;

final class ZoltBuildVersion {
    private static final String BUILD_VERSION = System.getProperty(ProjectVersionOverride.BUILD_PROPERTY);

    private ZoltBuildVersion() {
    }

    static String version() {
        return ProjectVersionOverride.resolveBuildVersion(BUILD_VERSION, ZoltCli.VERSION);
    }
}
