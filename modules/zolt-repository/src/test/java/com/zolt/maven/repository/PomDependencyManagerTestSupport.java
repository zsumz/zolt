package com.zolt.maven.repository;

import java.util.Map;

final class PomDependencyManagerTestSupport {
    private PomDependencyManagerTestSupport() {}

    static EffectiveRawPom effective(RawPomParser parser, String xml) {
        RawPom rawPom = parser.parse(xml);
        return new EffectiveRawPom(
                rawPom,
                java.util.List.of(),
                rawPom.groupId().orElseThrow(),
                rawPom.version().orElseThrow(),
                Map.copyOf(rawPom.properties()),
                rawPom.dependencyManagement());
    }
}
