# Netty Common Subset Adapter

Status: source-compatible `common` main-source subset comparison ready.

`scripts/benchmark-netty-compare` generates reviewable Zolt and Maven overlays
from a pinned local Netty checkout. The runner gives both tools the same
filtered `common` main Java sources, dependencies, and Java level, then records
the source count, commands, compiler difference, and package difference.

This is a useful Netty source-subset comparison, not a full Netty comparison.
The `benchmark-real-project` lane uses the same `common` coverage boundary for
its standard clean, no-op, and source-change comparison workflows.

Current pinned comparison source:

- upstream: `https://github.com/netty/netty`
- branch: `4.1`
- commit: `bb2ff68a1fb71cb4b0eb9a9e17b66c52aff680c6`
- upstream tool: Maven
- manifest: `docs/benchmarks/projects.json`

The comparison adapter is an overlay, not a fork:

- copy adapter files into the pinned upstream checkout during the benchmark run;
- leave upstream source untouched except for explicit benchmark source-change
  markers;
- record every included module and every omitted module;
- record whether JNI, generated sources, tests, integration tests, and packaging
  are covered;
- keep the Zolt commands and Maven commands side by side in the result artifact.

Current coverage:

- copied `common/src/main/java` sources, excluding Graal substitution sources
  that require Maven/native-image processing;
- cold dependency resolution and warm no-op compile/build rows;
- separate Zolt and Maven thin-jar rows, explicitly marked as not
  byte-identical.

Still omitted:

- the rest of the Netty reactor;
- native transports and generated native sources;
- Graal substitution processing and Maven plugin behavior;
- Netty's unreleased optional test tooling in the default main-source lane;
- test compilation and execution;
- equivalent release packaging;
- publishable test parity.

Publication rule: every Netty result must ship this adapter directory, the
upstream Maven logs, raw samples, deterministic summary, and module coverage
notes.
