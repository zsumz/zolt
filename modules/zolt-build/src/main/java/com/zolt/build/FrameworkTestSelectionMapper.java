package com.zolt.build;

import com.zolt.framework.FrameworkTestSelection;
import com.zolt.test.TestSelection;

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
