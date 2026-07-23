#!/bin/sh
# Emit a Java source into the step's declared output (a java-sources root).
set -eu
seed=$(cat "$ZOLT_PROJECT_ROOT/src/main/exec/seed.txt")
pkg="$ZOLT_OUTPUT_DIR/sh/zolt/canary/execprocess/generated"
mkdir -p "$pkg"
cat > "$pkg/Greeting.java" <<JAVA
package sh.zolt.canary.execprocess.generated;

public final class Greeting {
    private Greeting() {
    }

    public static String text() {
        return "$seed";
    }
}
JAVA
