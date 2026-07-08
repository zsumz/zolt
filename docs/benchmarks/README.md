# Benchmarks

Zolt benchmark claims should be backed by repeatable evidence, not a single
headline number.

The first public harness is `scripts/benchmark-competitors`. It generates the
same multi-module Java workspace shape for Zolt, Maven, and Gradle, then records
wall-clock samples for workflows that matter on larger projects:

- warm no-op build;
- leaf source change build;
- root library source change build.

The script writes raw JSON-lines samples, command logs, a JSON summary, and a
Markdown report under `target/benchmarks/competitors` by default.

```sh
scripts/benchmark-competitors --modules 40 --repeat 5
```

Useful variants:

```sh
scripts/benchmark-competitors --modules 100 --repeat 7
scripts/benchmark-competitors --include-gradle-daemon
scripts/benchmark-competitors --zolt ~/.zolt/bin/zolt
scripts/benchmark-competitors --skip-maven --skip-gradle --modules 200
```

## GitHub Actions

Use the manual `benchmarks` workflow for public runs. It installs or builds a
native Zolt binary, runs this harness, writes the generated Markdown report into
the job summary, and uploads the raw samples, JSON summary, and command logs as
workflow artifacts.

Use `zolt_source=release` for publishable comparisons. It installs the selected
release channel and avoids mixing Zolt build time into the benchmark setup. Use
`zolt_source=build` only when measuring the checked-out branch's native binary.
While this work is on the `benchmarks` branch, pushes to that branch run a tiny
release-native smoke automatically so the job summary can be reviewed before the
manual workflow is merged.

The workflow installs a pinned Gradle distribution directly instead of using
`gradle/actions/setup-gradle`. That keeps the GitHub summary dedicated to the
benchmark report and avoids unrelated Gradle action cache/build-scan summaries.

## Publishing Results

When publishing benchmark evidence:

- include the generated `report.md`, `summary.json`, and `samples.jsonl`;
- include tool versions, JDK version, OS, CPU architecture, module count, and
  repeat count;
- separate no-op, leaf-change, and root-change workflows;
- compare medians and keep raw samples available;
- say whether Gradle was measured with or without the daemon;
- avoid claims from machines with missing competitors, failed setup commands, or
  mixed cache states.

The README should stay conservative until this directory contains dated evidence
from a clean machine. A good public claim is specific: for example, "on this
machine, for this generated 100-module workspace, Zolt's median warm no-op build
was N ms versus Maven M ms and Gradle G ms."
