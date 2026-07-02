package sh.zolt.build.testruntime.execution;

import sh.zolt.framework.FrameworkTestSelection;
import sh.zolt.test.TestSelection;

final class FrameworkTestSelectionMapper {
    FrameworkTestSelection map(TestSelection selection) {
        if (selection == null) {
            return FrameworkTestSelection.empty();
        }
        return new FrameworkTestSelection(
                selection.classSelectors(),
                selection.methodSelectors().stream()
                        .map(method -> new FrameworkTestSelection.MethodSelector(
                                method.className(),
                                method.methodName()))
                        .toList(),
                selection.classNamePatterns(),
                selection.includedTags(),
                selection.excludedTags());
    }
}
