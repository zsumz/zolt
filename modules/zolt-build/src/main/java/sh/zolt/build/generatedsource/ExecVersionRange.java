package sh.zolt.build.generatedsource;

import sh.zolt.dependency.VersionComparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal semver-range guard for a process tool's {@code versionExpect} (e.g. {@code ">=10 <11"}).
 * Space-separated comparators are ANDed; each is one of {@code >= <= > < = == !=} followed by a
 * dotted-numeric version (a bare version means {@code =}). Comparison reuses {@link VersionComparator}.
 */
final class ExecVersionRange {
    private static final Pattern VERSION_TOKEN = Pattern.compile("\\d+(?:\\.\\d+)*");
    private static final VersionComparator COMPARATOR = new VersionComparator();

    private final List<Bound> bounds;

    private ExecVersionRange(List<Bound> bounds) {
        this.bounds = bounds;
    }

    static ExecVersionRange parse(String spec) {
        List<Bound> bounds = new ArrayList<>();
        for (String token : spec.trim().split("\\s+")) {
            if (!token.isBlank()) {
                bounds.add(Bound.parse(token));
            }
        }
        if (bounds.isEmpty()) {
            throw new IllegalArgumentException("versionExpect range `" + spec + "` has no comparators.");
        }
        return new ExecVersionRange(bounds);
    }

    boolean matches(String version) {
        return bounds.stream().allMatch(bound -> bound.matches(version));
    }

    /** The first dotted-numeric token in the probe output (a leading {@code v} is naturally skipped). */
    static Optional<String> extractVersion(String probeOutput) {
        Matcher matcher = VERSION_TOKEN.matcher(probeOutput);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    private record Bound(Operator operator, String version) {
        static Bound parse(String token) {
            for (Operator operator : Operator.MATCH_ORDER) {
                if (!operator.symbol.isEmpty() && token.startsWith(operator.symbol)) {
                    String version = token.substring(operator.symbol.length()).trim();
                    if (version.isEmpty()) {
                        throw new IllegalArgumentException("versionExpect comparator `" + token + "` has no version.");
                    }
                    return new Bound(operator, version);
                }
            }
            return new Bound(Operator.EQUAL, token);
        }

        boolean matches(String candidate) {
            return operator.test(COMPARATOR.compare(candidate, version));
        }
    }

    private enum Operator {
        GREATER_EQUAL(">="),
        LESS_EQUAL("<="),
        NOT_EQUAL("!="),
        EQUAL_EQUAL("=="),
        GREATER(">"),
        LESS("<"),
        EQUAL("=");

        // Two-character operators must be tried before their one-character prefixes.
        static final Operator[] MATCH_ORDER = {
            GREATER_EQUAL, LESS_EQUAL, NOT_EQUAL, EQUAL_EQUAL, GREATER, LESS, EQUAL
        };

        final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        boolean test(int comparison) {
            return switch (this) {
                case GREATER_EQUAL -> comparison >= 0;
                case LESS_EQUAL -> comparison <= 0;
                case NOT_EQUAL -> comparison != 0;
                case GREATER -> comparison > 0;
                case LESS -> comparison < 0;
                case EQUAL, EQUAL_EQUAL -> comparison == 0;
            };
        }
    }
}
