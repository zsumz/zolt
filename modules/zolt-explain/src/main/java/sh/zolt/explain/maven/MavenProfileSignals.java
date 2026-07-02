package sh.zolt.explain.maven;

import sh.zolt.explain.ExplainSignal;
import sh.zolt.explain.ExplainSignals;
import java.util.ArrayList;
import java.util.List;

final class MavenProfileSignals {
    private MavenProfileSignals() {
    }

    static List<ExplainSignal> signalsFor(String project, MavenProjectInspection inspection) {
        List<ExplainSignal> signals = new ArrayList<>();
        for (MavenProfileInspection profile : inspection.profiles()) {
            String activation = profile.activationHints().isEmpty()
                    ? "manual activation"
                    : String.join(", ", profile.activationHints());
            signals.add(ExplainSignals.MAVEN_PROFILE_DETECTED.signal(
                    project,
                    "Profile `" + profile.id() + "` is present with " + activation + "."));
            if (!profile.modules().isEmpty()) {
                signals.add(ExplainSignals.MAVEN_PROFILE_MODULES_DETECTED.signal(
                        project,
                        "Profile `" + profile.id() + "` declares module(s) "
                                + String.join(", ", profile.modules())
                                + "; static workspace emit omits these profile-gated members by default."));
            }
        }
        return signals;
    }

    static List<String> modules(MavenProjectInspection inspection) {
        return inspection.profiles().stream()
                .flatMap(profile -> profile.modules().stream())
                .distinct()
                .sorted()
                .toList();
    }
}
