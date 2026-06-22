package com.zolt.arch;

import java.util.Set;

record SourceFile(String packageName, Set<String> importedPackages) {
}
