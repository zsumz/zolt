package sh.zolt.framework;

import java.util.List;

public record FrameworkTestSelection(
        List<String> classSelectors,
        List<MethodSelector> methodSelectors,
        List<String> classNamePatterns,
        List<String> includedTags,
        List<String> excludedTags) {
    private static final FrameworkTestSelection EMPTY =
            new FrameworkTestSelection(List.of(), List.of(), List.of(), List.of(), List.of());

    public FrameworkTestSelection {
        classSelectors = classSelectors == null ? List.of() : List.copyOf(classSelectors);
        methodSelectors = methodSelectors == null ? List.of() : List.copyOf(methodSelectors);
        classNamePatterns = classNamePatterns == null ? List.of() : List.copyOf(classNamePatterns);
        includedTags = includedTags == null ? List.of() : List.copyOf(includedTags);
        excludedTags = excludedTags == null ? List.of() : List.copyOf(excludedTags);
    }

    public static FrameworkTestSelection empty() {
        return EMPTY;
    }

    public record MethodSelector(String className, String methodName) {}
}
