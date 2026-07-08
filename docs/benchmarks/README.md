# Benchmarks

Zolt benchmark claims should be backed by repeatable evidence, not a single
headline number.

The target suite is larger than the current harness: a generated enterprise-like
Java workload, pinned real-project lanes, and optional OpenAI summaries over the
structured result. See [plan.md](./plan.md) for the benchmark architecture.
The real-project lane manifest lives in [projects.json](./projects.json).

The public entrypoint is `scripts/benchmark-suite`. It runs selected benchmark
lanes, writes one suite-level summary, and keeps each lane's raw evidence under
the suite artifact. The generated Java workspace lane uses
`scripts/benchmark-competitors` underneath. It generates the same multi-module
Java workspace shape for Zolt, Maven, and Gradle, then records wall-clock samples
for workflows that matter on larger projects:

- first clean build as a single setup lane;
- warm no-op build;
- leaf source change build;
- root library source change build.

The script writes raw JSON-lines samples, command logs, a JSON summary, and a
Markdown report under `target/benchmarks/competitors` by default.

```sh
scripts/benchmark-suite --modules 40 --repeat 5 --include-gradle-daemon
```

Useful variants:

```sh
scripts/benchmark-suite --modules 100 --repeat 7 --include-gradle-daemon
scripts/benchmark-suite --zolt ~/.zolt/bin/zolt
scripts/benchmark-suite --generated-summary target/benchmarks/competitors/generated-java-workspace/summary.json
scripts/benchmark-suite --real-projects spring-petclinic,apache-commons-cli --repeat 5
scripts/benchmark-suite --real-project netty --repeat 3 --include-gradle-daemon
scripts/benchmark-suite --real-project netty --real-project-sample-timeout 3600
scripts/benchmark-suite --skip-generated --real-project netty --repeat 1 --real-project-sample-timeout 3600
scripts/benchmark-suite --skip-generated --real-projects spring-petclinic,netty --real-project-dry-run
scripts/benchmark-competitors --modules 200 --skip-maven --skip-gradle
```

The generated-lane script can still be used directly while debugging:

```sh
scripts/benchmark-competitors --modules 40 --repeat 5 --include-gradle-daemon
```

After a direct generated-lane run, generate a suite-level summary:

```sh
scripts/benchmark-suite-summary \
  --summary target/benchmarks/competitors/summary.json \
  --output target/benchmarks/competitors/suite-summary.json
```

That writes:

- `summary-brief.md` for a deterministic suite summary;
- `suite-summary.json` as the stable contract for CI, artifacts, and model
  summarization;
- lane detail files under `generated-java-workspace/`;
- `llm-summary.md` as a compatibility alias for the deterministic summary.

To generate a model summary locally:

```sh
OPENAI_API_KEY=... scripts/benchmark-openai-summary \
  --input target/benchmarks/competitors/suite-summary.json
```

Use `--dry-run` to write the request payload without calling the API.

No OpenAI call happens unless `OPENAI_API_KEY` is configured. CI still publishes
the deterministic summary when the key is absent.

## GitHub Actions

Use the manual `benchmarks` workflow for public runs. It installs or builds a
native Zolt binary, runs the suite harness, writes the deterministic compact summary
into the job summary, optionally appends a model-generated summary, and uploads
the report, raw samples, JSON summaries, prompt context, and command logs as
workflow artifacts.

To enable model-generated summaries in GitHub Actions, add a repository Actions
secret named `OPENAI_API_KEY`. Optional repository variables:

- `OPENAI_MODEL`, default `gpt-5.5`;
- `OPENAI_REASONING_EFFORT`, default `high`;
- `BENCHMARK_AI_SUMMARY=false` to disable the model step without removing the
  secret.

Use `zolt_source=release` for publishable comparisons. It installs the selected
release channel and avoids mixing Zolt build time into the benchmark setup. Use
`zolt_source=build` only when measuring the checked-out branch's native binary.
While this work is on the `benchmarks` branch, pushes to that branch run a
40-module, 5-repeat, released native-Zolt generated benchmark plus the Spring
PetClinic and Apache Commons CLI upstream Maven baselines automatically so the
combined job summary can be reviewed before the manual workflow is merged.
Manual runs default to 100 modules, 7 repeats, and Gradle daemon coverage.
For a Netty-only validation run, dispatch the workflow with
`skip_generated=true`, `real_projects=netty`, `repeat=1`, and
`real_project_sample_timeout=3600`.

The workflow installs a pinned Gradle distribution directly instead of using
`gradle/actions/setup-gradle`. That keeps the GitHub summary dedicated to the
benchmark report and avoids unrelated Gradle action cache/build-scan summaries.
Uploaded real-project artifacts keep summaries, samples, and command logs, but
exclude the checked-out upstream source trees.
Adapter groundwork under `benchmarks/adapters/` is included so future
real-project comparisons carry their coverage notes with the data.

## Real Projects

Generated workspaces are useful for controlled scaling evidence, but they are
not enough for public performance claims. Real-project benchmarks should use
pinned upstream commits plus Zolt adapters that build the same meaningful source
set. See [real-projects.md](./real-projects.md) for the project policy and
initial candidate suite.
`projects.json` is the machine-readable version used by the suite runner.

The first real-project runner records upstream-tool baselines only:

```sh
scripts/benchmark-real-project --project spring-petclinic --repeat 5
scripts/benchmark-real-project --project apache-commons-cli --repeat 5
scripts/benchmark-real-project --project netty --repeat 3 --sample-timeout 3600
```

Those runs clone the pinned upstream commit into the benchmark output directory,
record upstream Maven or Gradle timings, and write a suite-compatible lane summary.
They are not Zolt comparisons until an adapter is checked in and included with
the artifact.

## Publishing Results

When publishing benchmark evidence:

- include the generated `report.md`, `summary.json`, `suite-summary.json`, and
  `samples.jsonl`;
- include `summary-brief.md` and, when generated, `summary-ai.json` plus
  `summary-ai.md`;
- include tool versions, JDK version, OS, CPU architecture, module count, and
  repeat count;
- keep the first clean build separate from repeated no-op, leaf-change, and
  root-change workflows;
- compare medians and keep raw samples available;
- say whether Gradle was measured with or without the daemon;
- avoid claims from machines with missing competitors, failed setup commands, or
  mixed cache states.

The README should stay conservative until this directory contains dated evidence
from a clean machine. A good public claim is specific: for example, "on this
machine, for this generated 100-module workspace, Zolt's median warm no-op build
was N ms versus Maven M ms and Gradle G ms."
