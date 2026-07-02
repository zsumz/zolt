package sh.zolt.resolve.traversal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.maven.repository.RawPomDependency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DependencyTraversalPolicyTest {
    private final DependencyNormalizer normalizer = new DependencyNormalizer();
    private final DependencyTraversalPolicy policy = new DependencyTraversalPolicy();

    @Test
    void skipsOptionalTransitiveDependencies() {
        DependencyTraversalDecision decision = policy.decide(optionalDependency(), false);

        assertFalse(decision.included());
        assertEquals("optional transitive dependency", decision.reason());
    }

    @Test
    void directlyDeclaredOptionalDependencyStillResolves() {
        DependencyTraversalDecision decision = policy.decide(optionalDependency(), true);

        assertTrue(decision.included());
        assertEquals("direct dependency", decision.reason());
    }

    @Test
    void nonOptionalTransitiveDependencyResolves() {
        DependencyTraversalDecision decision = policy.decide(nonOptionalDependency(), false);

        assertTrue(decision.included());
        assertEquals("non-optional transitive dependency", decision.reason());
    }

    private NormalizedDependency optionalDependency() {
        return normalizer.normalize(new RawPomDependency(
                "com.example",
                "optional-lib",
                Optional.of("1.0.0"),
                Optional.of("compile"),
                Optional.empty(),
                Optional.empty(),
                true,
                List.of()));
    }

    private NormalizedDependency nonOptionalDependency() {
        return normalizer.normalize(new RawPomDependency(
                "com.example",
                "regular-lib",
                Optional.of("1.0.0"),
                Optional.of("compile"),
                Optional.empty(),
                Optional.empty(),
                false,
                List.of()));
    }
}
